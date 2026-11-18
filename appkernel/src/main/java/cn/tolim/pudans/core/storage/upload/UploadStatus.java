package cn.tolim.pudans.core.storage.upload;

import java.time.Instant;
import java.util.List;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;

public record UploadStatus(
        String fileId,
        String policy,
        String storageKey,
        long chunkSize,
        int partCount,
        Instant expiresAt,
        List<UploadedPart> uploadedParts,
        boolean presigned) {}
