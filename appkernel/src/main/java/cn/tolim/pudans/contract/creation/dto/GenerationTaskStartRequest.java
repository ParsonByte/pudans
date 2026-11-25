package cn.tolim.pudans.contract.creation.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationTaskStartRequest(
        String mediaType,
        String operation,
        UUID modelId,
        String prompt,
        Map<String, Object> settings,
        List<String> inputAssetIds,
        UUID sessionId,
        UUID runId,
        UUID parentTaskId,
        UUID parentAssetId,
        UUID quotaReservationId) {}
