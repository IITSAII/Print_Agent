package com.iitsaii.printagent.dto;

public record PrintQueueResponse(
        String sessionId,
        String finalImageUrl,
        String frameType,
        Boolean filterBw,
        Integer filterBrightness
) {}
