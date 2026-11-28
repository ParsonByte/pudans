package cn.tolim.pudans.contract.repository.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Stored file metadata
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileMetadataResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // File ID
    private String id;

    // Tenant ID
    private String tenantId;

    // Application code
    private String appCode;

    // File category
    private String category;

    // Storage provider code
    private String provider;

    // Storage object key
    private String objectKey;

    // Original file name
    private String originalName;

    // Content type
    private String contentType;

    // File extension
    private String extension;

    // File size in bytes
    private Long size;

    // SHA-256 checksum of the content
    private String checksumSha256;

    // File status: ACTIVE or DELETED
    private String status;

    // Creator
    private String createdBy;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // Last modification time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // Custom metadata
    private Map<String, String> metadata;
}
