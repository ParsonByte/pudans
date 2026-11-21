package cn.tolim.pudans.core.storage.driver;

import java.time.Instant;
import reactor.core.publisher.Mono;

public interface DirectUploadDriver {

    // 创建客户端直传对象存储所需的凭证。
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
