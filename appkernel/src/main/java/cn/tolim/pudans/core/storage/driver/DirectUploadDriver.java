package cn.tolim.pudans.core.storage.driver;

import java.time.Instant;
import reactor.core.publisher.Mono;

public interface DirectUploadDriver {

    Mono<DirectUploadCredential> createDirectUpload(StorageDirectUploadRequest request);

    record StorageDirectUploadRequest(String key, long size, String contentType, Instant expiresAt) {}

    record DirectUploadCredential(
            String provider,
            String uploadUrl,
            String token,
            String policy,
            String signature,
            String key,
            String contentType,
            Instant expiresAt) {}
}
