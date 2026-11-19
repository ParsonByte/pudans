package cn.tolim.pudans.core.storage.upload;

import java.time.Instant;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.DirectUploadCredential;

public record UploadTicket(
        String fileId,
        String policy,
        String storageKey,
        long chunkSize,
        int partCount,
        Instant expiresAt,
        String uploadToken,
        boolean presigned,
        DirectUploadCredential directUpload) {}
