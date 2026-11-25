package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssetReuseDraft(
        UUID sourceAssetId,
        UUID parentAssetId,
        String storageFileId,
        UUID generationTaskId,
        String kind,
        String source,
        String prompt,
        Map<String, Object> settings,
        List<String> tags,
        Map<String, Object> metadata,
        Instant reusedAt) {}
