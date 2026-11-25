package cn.tolim.pudans.contract.ai.dto;

import java.time.Instant;

public record AiChannelTestResult(
        boolean success,
        String protocol,
        Integer statusCode,
        String requestId,
        String code,
        String message,
        Instant testedAt) {}
