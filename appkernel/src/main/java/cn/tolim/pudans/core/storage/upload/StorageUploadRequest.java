package cn.tolim.pudans.core.storage.upload;

import java.util.Map;

public record StorageUploadRequest(
        String policy,
        String owner,
        String module,
        String originPath,
        String originalName,
        String contentType,
        long size,
        Map<String, String> attributes,
        String sha256) {}
