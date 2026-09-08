package com.iitsaii.printagent.printer;

import com.iitsaii.printagent.config.PrintAgentConfig;

import javax.imageio.ImageIO;
import javax.print.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.nio.file.Path;

public class PrinterService {

    private static final double PAPER_WIDTH = 6.0 * 72.0;
    private static final double PAPER_HEIGHT = 4.0 * 72.0;

    private static final double STRIP_WIDTH = 6.0 * 72.0;
    private static final double STRIP_HEIGHT = 2.0 * 72.0;

    public void print(Path imagePath) throws Exception{

        System.out.println("[PRINT] 출력 시작");
        System.out.println("[PRINT] 원본 이미지 = " + imagePath);

        PrintService printer = findPrinter();

        if (printer == null) {
            throw new RuntimeException("DNP 프린터를 찾을 수 없습니다.");
        }

        BufferedImage source = ImageIO.read(imagePath.toFile());

        if (source == null) {
            throw new RuntimeException("이미지를 읽을 수 없습니다." + imagePath);
        }

        System.out.println("[PRINT] 원본 이미지 크기 = " + source.getWidth() + "x" + source.getHeight());

        PrinterJob printerJob = PrinterJob.getPrinterJob();

        printerJob.setPrintService(printer);

        PageFormat pageFormat = createPageFormat();

        printerJob.setPrintable(createPrintable(source), pageFormat);

        System.out.println("[PRINT] PrinterJob 출력 시작");

        printerJob.print();

        System.out.println("[PRINT] PrinterJob 출력 종료");
    }

    private PageFormat createPageFormat() {

        PageFormat pageFormat = new PageFormat();

        Paper paper = new Paper();

        paper.setSize(PAPER_WIDTH, PAPER_HEIGHT);

        paper.setImageableArea(0, 0, PAPER_WIDTH, PAPER_HEIGHT);

        pageFormat.setPaper(paper);

        pageFormat.setOrientation(PageFormat.LANDSCAPE);

        return pageFormat;
    }

    private Printable createPrintable(BufferedImage source) {

        return(graphics, pageFormat, pageIndex) -> {

            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2 = (Graphics2D) graphics;

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            double imageableX = pageFormat.getImageableX();
            double imageableY = pageFormat.getImageableY();

            double imageableWidth = pageFormat.getImageableWidth();
            double imageableHeight = pageFormat.getImageableHeight();

            double stripHeight = imageableHeight / 2.0;

            drawRotatedStrip(g2, source, imageableX, imageableY, imageableWidth, stripHeight);
            drawRotatedStrip(g2, source, imageableX, imageableY + stripHeight, imageableWidth, stripHeight);

            return Printable.PAGE_EXISTS;
        };
    }

    private void drawRotatedStrip(Graphics2D g2, BufferedImage source, double x, double y, double targetWidth, double targetHeight) {

        double rotatedWidth = source.getHeight();
        double rotatedHeight = source.getWidth();

        double scaleX = targetWidth / rotatedWidth;
        double scaleY = targetHeight / rotatedHeight;

        double scale = Math.min(scaleX, scaleY);

        double drawWidth = rotatedWidth * scale;
        double drawHeight = rotatedHeight * scale;

        double drawX = x + (targetWidth - drawWidth) / 2.0;

        double drawY = y + (targetHeight - drawHeight) / 2.0;

        AffineTransform transform = new AffineTransform();

        transform.translate(drawX + drawWidth, drawY);

        transform.rotate(Math.PI / 2.0);

        transform.scale(scale, scale);

        g2.drawImage(source, transform, null);
    }

    private PrintService findPrinter() {

        PrintService[] printers = PrintServiceLookup.lookupPrintServices(null, null);

        System.out.println("[PRINT] Java에서 검색된 프린터 수 = " + printers.length);

        for (PrintService printer : printers) {
            System.out.println("[PRINT] Java 프린터 이름 = [" + printer.getName() + "]");

            if (printer.getName().equals(PrintAgentConfig.PRINTER_NAME)) {
                return printer;
            }
        }

        return null;
    }
}
