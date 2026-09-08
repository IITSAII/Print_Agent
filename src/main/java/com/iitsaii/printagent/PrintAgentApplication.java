package com.iitsaii.printagent;

import com.iitsaii.printagent.client.BackendClient;
import com.iitsaii.printagent.config.PrintAgentConfig;
import com.iitsaii.printagent.downloader.ImageDownloader;
import com.iitsaii.printagent.dto.PrintQueueResponse;
import com.iitsaii.printagent.printer.PrinterService;

import java.nio.file.Path;

public class PrintAgentApplication {
    public static void main(String[] args) {

        BackendClient backendClient = new BackendClient();
        ImageDownloader imageDownloader = new ImageDownloader();

        while (true) {

            try {
                PrintQueueResponse job = backendClient.getQueue();

                if (job == null) {
                    System.out.println("대기 중인 인쇄 작업이 없습니다.");
                } else {
                    PrinterService printerService = new PrinterService();

                    Path imagePath = imageDownloader.saveImage(job.finalImageUrl(), job.sessionId());
                    System.out.println("다운로드 완료 : " +imagePath);

                    printerService.print(imagePath);
                    System.out.println("인쇄 완료 처리");

                    backendClient.completePrint(job.sessionId());
                }
            } catch (Exception e) {
                System.out.println("Queue 조회 실패");
                e.printStackTrace();
            }

            try {
                Thread.sleep(PrintAgentConfig.POLLING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}