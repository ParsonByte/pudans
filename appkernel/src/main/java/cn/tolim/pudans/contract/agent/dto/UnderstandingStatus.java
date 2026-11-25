package cn.tolim.pudans.contract.agent.dto;

import java.time.Instant;

public record UnderstandingStatus(
        boolean extractionConfigured,
        boolean vectorConfigured,
        String extractionReasonCode,
        String vectorReasonCode,
        Instant checkedAt) {}
