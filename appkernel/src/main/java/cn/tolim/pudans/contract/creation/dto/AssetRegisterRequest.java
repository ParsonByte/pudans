package cn.tolim.pudans.contract.creation.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssetRegisterRequest(
        String storageFileId,
        String kind,
        String source,
        UUID generationTaskId,
        UUID parentAssetId,
        String name,
        String description,
        String prompt,
        Map<String, Object> settings,
        String contentType,
        Long size,
        Integer width,
        Integer height,
        Long durationMs,
        List<String> tags,
        Map<String, Object> metadata) {}
