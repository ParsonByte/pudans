package cn.tolim.pudans.repository.contract;

import java.time.LocalDateTime;
import java.util.Map;

public record FileMetadataResponse(String id, String tenantId, String appCode, String category,
                                   String provider, String objectKey, String originalName,
                                   String contentType, String extension, long size,
                                   String checksumSha256, String status, String createdBy,
                                   LocalDateTime createTime, LocalDateTime updateTime,
                                   Map<String, String> metadata) {
}