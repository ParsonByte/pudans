package cn.tolim.pudans.contract.creation.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GenerationTaskStatusView(
        GenerationTaskSummary task,
        UUID sessionId,
        UUID runId,
        UUID parentTaskId,
        UUID parentAssetId,
        Map<String, Object> settings,
        List<String> inputAssetIds,
        List<GenerationTaskAttemptView> attempts) {}
