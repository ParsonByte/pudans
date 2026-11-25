package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GenerationTaskResultView(
        UUID taskId,
        String status,
        List<String> resultAssetIds,
        List<ArchivedResult> storageFiles,
        String registrationState,
        String message,
        Instant finishedAt) {

    public record ArchivedResult(
            String fileId,
            String originalName,
            String contentType,
            long size,
            String mediaType) {}
}
