package com.iitsaii.printagent.printer;

import com.iitsaii.printagent.config.PrintAgentConfig;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Iterator;

public class PrinterService {

    // PPD(DNP-DS-RX1.ppd.gz)의 "*PaperDimension 300dnp6x4"가 442.56 x 297.6pt로 선언되어 있다
    // (정확히 6x4인치가 아니라 6.1467 x 4.1333인치). 이전에 정확히 6x4인치(1800x1200px)로 캔버스를
    // 만들었더니 실제 페이지 크기보다 작아서 CUPS가 재배치/스케일링하며 잘림/여백이 생겼다.
    // 300dpi 기준으로 픽셀 환산: 442.56pt / 72 * 300 = 1844px, 297.6pt / 72 * 300 = 1240px.
    private static final int CANVAS_WIDTH_PX = 1844;
    private static final int CANVAS_HEIGHT_PX = 1240;

    private static final int DPI = 300;

    /**
     * javax.print.PrinterJob으로는 PPD의 커스텀 옵션(Cutter 등)을 지정할 방법이 없어
     * (표준 javax.print 속성에 대응하는 게 없음) 드라이버 기본값에만 의존하게 되고, 그 결과
     * 가로/세로가 뒤집히거나 커터가 동작하지 않는 문제가 있었다. 대신 완성된 이미지를 파일로
     * 렌더링한 뒤 lp 명령을 직접 실행해 -o 옵션으로 PageSize/Cutter/Resolution을 명시적으로
     * 지정한다 - 이 방식은 lp로 직접 인쇄했을 때 정상 동작했던 경로와 동일하다.
     */
    public void print(Path imagePath) throws Exception {

        System.out.println("[PRINT] 출력 시작");
        System.out.println("[PRINT] 원본 이미지 = " + imagePath);

        BufferedImage source = ImageIO.read(imagePath.toFile());

        if (source == null) {
            throw new RuntimeException("이미지를 읽을 수 없습니다." + imagePath);
        }

        System.out.println("[PRINT] 원본 이미지 크기 = " + source.getWidth() + "x" + source.getHeight());

        BufferedImage canvas = composeCanvas(source);

        // 디버깅을 위해 임시 파일 대신 고정 경로에 저장한다 (DPI/스케일 문제 조사가 끝나면
        // 다시 Files.createTempFile + finally에서 삭제하는 방식으로 되돌린다).
        Path tempFile = Path.of(System.getProperty("user.home"), "Downloads", "print-agent-debug.jpg");

        try {
            System.out.println("[PRINT] 캔버스 크기 = " + canvas.getWidth() + "x" + canvas.getHeight());

            // ImageIO.write(jpg)는 DPI 메타데이터를 72로 기본 저장한다. 이전에 이 값이 72로
            // 저장되는 바람에 CUPS/드라이버가 이미지의 물리적 크기를 (1844px/72dpi=25.6인치처럼)
            // 실제보다 훨씬 크게 해석해서, print-scaling=none을 줘도 위치/크기가 계속 어긋났다.
            // JPEG에 300dpi를 명시적으로 박아 이 오해석을 없앤다.
            writeJpegWithDpi(canvas, tempFile.toFile(), DPI);

            System.out.println("[PRINT] lp 명령으로 전송 시작: " + tempFile);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "lp",
                    "-d", PrintAgentConfig.CUPS_PRINTER_NAME,
                    "-o", "PageSize=300dnp6x4",
                    "-o", "Cutter=2Inch",
                    "-o", "Resolution=300x300dpi",
                    // 캔버스를 PPD의 실제 PaperDimension과 정확히 같은 픽셀 크기로 만들었으므로,
                    // CUPS가 추가로 비율을 맞추거나(fit) 자르지(crop) 않고 있는 그대로 찍도록 한다.
                    "-o", "print-scaling=none",
                    tempFile.toString()
            );
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[LP] " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("lp 명령 실행 실패 (exitCode=" + exitCode + ")");
            }

            System.out.println("[PRINT] lp 명령 전송 완료");
        } finally {
            // 디버깅 중이라 tempFile을 지우지 않고 남겨둔다. ~/Downloads/print-agent-debug.jpg에서 확인 가능.
        }
    }

    /**
     * JPEG의 JFIF 헤더(app0JFIF 마커)에 dpi를 직접 명시해 저장한다.
     * 표준 메타데이터(HorizontalPixelSize 등)로 시도했을 때는 그 노드가 "dpi"가 아니라
     * "픽셀 1개의 물리적 크기(mm)"를 의미하는 걸 놓쳐 값을 반대로(역수) 넣는 바람에 반영되지
     * 않았다. JFIF 마커에 직접 Xdensity/Ydensity/resUnits를 쓰는 게 더 확실하다.
     */
    private void writeJpegWithDpi(BufferedImage image, java.io.File output, int dpi) throws Exception {

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

        if (!writers.hasNext()) {
            throw new RuntimeException("JPEG ImageWriter를 찾을 수 없습니다.");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);

        IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);

        String nativeFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(nativeFormat);

        IIOMetadataNode jfif = findOrCreateApp0Jfif(root);
        jfif.setAttribute("resUnits", "1"); // 1 = dots per inch
        jfif.setAttribute("Xdensity", Integer.toString(dpi));
        jfif.setAttribute("Ydensity", Integer.toString(dpi));

        metadata.setFromTree(nativeFormat, root);

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
    }

    /** JPEG 네이티브 메타데이터 트리에서 JPEGvariety/app0JFIF 노드를 찾고, 없으면 새로 만든다. */
    private IIOMetadataNode findOrCreateApp0Jfif(IIOMetadataNode root) {

        IIOMetadataNode jpegVariety = findChild(root, "JPEGvariety");

        if (jpegVariety == null) {
            jpegVariety = new IIOMetadataNode("JPEGvariety");
            root.insertBefore(jpegVariety, root.getFirstChild());
        }

        IIOMetadataNode jfif = findChild(jpegVariety, "app0JFIF");

        if (jfif == null) {
            jfif = new IIOMetadataNode("app0JFIF");
            jfif.setAttribute("majorVersion", "1");
            jfif.setAttribute("minorVersion", "2");
            jfif.setAttribute("thumbWidth", "0");
            jfif.setAttribute("thumbHeight", "0");
            jpegVariety.appendChild(jfif);
        }

        return jfif;
    }

    private IIOMetadataNode findChild(IIOMetadataNode parent, String name) {

        org.w3c.dom.NodeList children = parent.getElementsByTagName(name);

        return children.getLength() > 0 ? (IIOMetadataNode) children.item(0) : null;
    }

    /** 6x4 캔버스(300dpi)에 원본 이미지를 90도 회전시켜 2등분(2Inch 컷 라인 기준)해 그린다. */
    private BufferedImage composeCanvas(BufferedImage source) {

        BufferedImage canvas = new BufferedImage(CANVAS_WIDTH_PX, CANVAS_HEIGHT_PX, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2 = canvas.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, CANVAS_WIDTH_PX, CANVAS_HEIGHT_PX);

        int stripHeight = CANVAS_HEIGHT_PX / 2;

        drawRotatedStrip(g2, source, 0, 0, CANVAS_WIDTH_PX, stripHeight);
        drawRotatedStrip(g2, source, 0, stripHeight, CANVAS_WIDTH_PX, stripHeight);

        g2.dispose();

        return canvas;
    }

    private void drawRotatedStrip(Graphics2D g2, BufferedImage source, double x, double y, double targetWidth, double targetHeight) {

        double rotatedWidth = source.getHeight();
        double rotatedHeight = source.getWidth();

        double scaleX = targetWidth / rotatedWidth;
        double scaleY = targetHeight / rotatedHeight;

        // 비율을 유지한 채 "맞추기"(min)를 쓰면 원본과 스트립의 비율이 완전히 같지 않은 이상
        // 남는 공간이 흰 여백으로 남는다 (두 스트립이 만나는 정중앙에 몰려 눈에 띄게 보였다).
        // 대신 "꽉 채우기"(max)로 바꾸고 넘치는 부분은 클리핑으로 잘라낸다 - 이미지 가장자리가
        // 아주 살짝(1% 미만) 잘리지만 흰 여백 없이 스트립을 완전히 채울 수 있다.
        double scale = Math.max(scaleX, scaleY);

        double drawWidth = rotatedWidth * scale;
        double drawHeight = rotatedHeight * scale;

        double drawX = x + (targetWidth - drawWidth) / 2.0;

        double drawY = y + (targetHeight - drawHeight) / 2.0;

        AffineTransform transform = new AffineTransform();

        transform.translate(drawX + drawWidth, drawY);

        transform.rotate(Math.PI / 2.0);

        transform.scale(scale, scale);

        // 꽉 채우기(fill)로 그리면 이미지가 target 영역보다 커져 넘칠 수 있으므로,
        // 다른 스트립 영역을 침범하지 않도록 target 영역으로 클리핑한다.
        Shape originalClip = g2.getClip();
        g2.clip(new Rectangle2D.Double(x, y, targetWidth, targetHeight));

        g2.drawImage(source, transform, null);

        g2.setClip(originalClip);
    }
}
