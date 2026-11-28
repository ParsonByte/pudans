package cn.tolim.pudans.repository.service;

import cn.tolim.pudans.repository.storage.StorageProviderType;

import java.util.Map;

public record FileUploadCommand(String tenantId, String appCode, String category,
                                String originalName, String contentType, long size,
                                String createdBy, Map<String, String> metadata,
                                StorageProviderType provider, ContentSource content) {

    public FileUploadCommand {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}