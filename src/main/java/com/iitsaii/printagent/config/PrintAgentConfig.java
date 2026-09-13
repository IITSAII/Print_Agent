package com.iitsaii.printagent.config;

public class PrintAgentConfig {

    public static final String BASE_URL = "https://api.itsai.co.kr";

    public static final String HOT_FOLDER = "/Users/sai/Downloads/images";

    public static final long POLLING_INTERVAL_MS = 5000;

    public static final String PRINTER_NAME = "Dai Nippon Printing DS-RX1";

    public static final String CUPS_PRINTER_NAME = "Dai_Nippon_Printing_DS_RX1";

    /** nginx에서 /api/print/queue 요청에 요구하는 비밀 헤더 값. 정식 agent만 이 값을 알고 있다. */
    public static final String AGENT_TOKEN = "pb-agent-2026-x7q2m9";
}
