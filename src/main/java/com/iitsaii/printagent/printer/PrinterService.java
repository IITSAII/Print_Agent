package com.iitsaii.printagent.printer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Iterator;

public class PrinterService {

    private static final int CANVAS_WIDTH_PX = 1800;
    private static final int CANVAS_HEIGHT_PX = 1200;
    private static final int DPI = 300;

    public void print(Path imagePath, Integer quantity) throws Exception {

        System.out.println("[PRINT] 출력 시작");
        System.out.println("[PRINT] image path = " + imagePath);

        BufferedImage sourceImage = ImageIO.read(imagePath.toFile());

        if (sourceImage == null) {
            throw new RuntimeException("이미지 로드 실패: " + imagePath);
        }

        System.out.println(
                "[PRINT] source size = "
                        + sourceImage.getWidth()
                        + "x"
                        + sourceImage.getHeight()
        );

        BufferedImage canvas = composeCanvas(sourceImage);

        File outputFile = Path.of(
                System.getProperty("user.home"),
                "Downloads",
                "print-agent-debug.jpg"
        ).toFile();

        System.out.println(
                "[PRINT] composed canvas = "
                        + canvas.getWidth()
                        + "x"
                        + canvas.getHeight()
        );

        writeJpegWithDpi(canvas, outputFile, DPI);

        System.out.println("[PRINT] JPEG 저장 완료 : " + outputFile.getAbsolutePath());

        Integer paperCount = switch (quantity) {
            case 2 -> 1;
            case 4 -> 2;
            case 6 -> 3;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 출력 수량: " + quantity
            );
        };

        for (int i = 1; i <= paperCount; i++) {
            ProcessBuilder pb = new ProcessBuilder(
                    "lp",
                    "-d", "Dai_Nippon_Printing_DS_RX1",
                    "-o", "PageSize=300dnp6x4",
                    "-o", "Cutter=2Inch",
                    "-o", "Resolution=300x300dpi",
                    "-o", "print-scaling=none",
                    outputFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[lp] " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                        "lp 명령 실패. exitCode=" + exitCode
                );
            }

            System.out.println("[PRINT] lp 명령 전송 완료");
        }
    }

    private BufferedImage composeCanvas(BufferedImage sourceImage) {

        BufferedImage canvas = new BufferedImage(
                CANVAS_WIDTH_PX,
                CANVAS_HEIGHT_PX,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = canvas.createGraphics();

        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, CANVAS_WIDTH_PX, CANVAS_HEIGHT_PX);

            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            drawRotatedStrip(g, sourceImage, 0, 0, 1800, 600);
            drawRotatedStrip(g, sourceImage, 0, 600, 1800, 600);

        } finally {
            g.dispose();
        }

        return canvas;
    }

    private void drawRotatedStrip(
            Graphics2D g,
            BufferedImage source,
            int x,
            int y,
            int targetWidth,
            int targetHeight
    ) {

        int rotatedWidth = source.getHeight();
        int rotatedHeight = source.getWidth();

        double scale = Math.min(
                (double) targetWidth / rotatedWidth,
                (double) targetHeight / rotatedHeight
        );

        int drawWidth = (int) Math.round(rotatedWidth * scale);
        int drawHeight = (int) Math.round(rotatedHeight * scale);

        int drawX = x + (targetWidth - drawWidth) / 2;
        int drawY = y + (targetHeight - drawHeight) / 2;

        Graphics2D g2 = (Graphics2D) g.create();

        try {
            g2.translate(
                    drawX + drawWidth / 2.0,
                    drawY + drawHeight / 2.0
            );

            g2.rotate(Math.toRadians(90));

            g2.drawImage(
                    source,
                    -drawHeight / 2,
                    -drawWidth / 2,
                    drawHeight,
                    drawWidth,
                    null
            );

        } finally {
            g2.dispose();
        }
    }

    /**
     * JPEG에 JFIF DPI(300x300)를 직접 기록.
     */
    private void writeJpegWithDpi(
            BufferedImage image,
            File file,
            int dpi
    ) throws Exception {

        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName("jpg");

        if (!writers.hasNext()) {
            throw new RuntimeException("JPEG ImageWriter를 찾을 수 없습니다.");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        ImageTypeSpecifier type =
                ImageTypeSpecifier.createFromBufferedImageType(
                        BufferedImage.TYPE_INT_RGB
                );

        IIOMetadata metadata =
                writer.getDefaultImageMetadata(type, param);

        String formatName = metadata.getNativeMetadataFormatName();

        IIOMetadataNode root =
                (IIOMetadataNode) metadata.getAsTree(formatName);

        IIOMetadataNode jfif = findOrCreateApp0Jfif(root);

        jfif.setAttribute("resUnits", "1");
        jfif.setAttribute("Xdensity", String.valueOf(dpi));
        jfif.setAttribute("Ydensity", String.valueOf(dpi));

        metadata.setFromTree(formatName, root);

        try (FileImageOutputStream output =
                     new FileImageOutputStream(file)) {

            writer.setOutput(output);

            writer.write(
                    metadata,
                    new IIOImage(image, null, metadata),
                    param
            );
        }

        writer.dispose();
    }

    private IIOMetadataNode findOrCreateApp0Jfif(
            IIOMetadataNode root
    ) {

        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i) instanceof IIOMetadataNode node) {
                if ("app0JFIF".equals(node.getNodeName())) {
                    return node;
                }
            }
        }

        IIOMetadataNode node = new IIOMetadataNode("app0JFIF");

        node.setAttribute("majorVersion", "1");
        node.setAttribute("minorVersion", "2");
        node.setAttribute("thumbWidth", "0");
        node.setAttribute("thumbHeight", "0");

        root.appendChild(node);

        return node;
    }
}