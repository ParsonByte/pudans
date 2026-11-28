package cn.tolim.pudans.repository.entity;

import cn.tolim.pudans.repository.storage.StorageProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "pudans_repository_file", indexes = {
        @Index(name = "idx_repository_file_tenant", columnList = "tenant_id"),
        @Index(name = "idx_repository_file_tenant_app", columnList = "tenant_id, app_code"),
        @Index(name = "idx_repository_file_object_key", columnList = "tenant_id, object_key"),
        @Index(name = "idx_repository_file_tenant_created", columnList = "tenant_id, create_time")
})
public class RepositoryFileEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 3298864157889078102L;

    @Id
    @Column(name = "id_", length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 128, nullable = false)
    private String tenantId;

    @Column(name = "app_code", length = 128)
    private String appCode;

    @Column(name = "category_", length = 128)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32, nullable = false)
    private StorageProviderType provider;

    @Column(name = "bucket_", length = 255)
    private String bucket;

    @Column(name = "object_key", length = 1024, nullable = false)
    private String objectKey;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "extension_", length = 64)
    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_", length = 32, nullable = false)
    private FileStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_")
    private Map<String, String> metadata;

    @Column(name = "create_by", length = 128)
    private String createBy;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
        if (status == null) {
            status = FileStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}