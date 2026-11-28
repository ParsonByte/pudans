package cn.tolim.pudans.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "pudans_search_document",
        uniqueConstraints = @UniqueConstraint(name = "uk_search_document_id",
                columnNames = {"app_code", "index_code", "document_id"}),
        indexes = {
                @Index(name = "idx_search_document_scope",
                        columnList = "app_code,index_code,update_time"),
                @Index(name = "idx_search_document_owner", columnList = "app_code,owner_id"),
                @Index(name = "idx_search_document_org", columnList = "app_code,org_id")
        })
public class SearchDocumentEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_", nullable = false, length = 64)
    private String id;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "index_code", nullable = false, length = 100)
    private String indexCode;

    @Column(name = "document_id", nullable = false, length = 200)
    private String documentId;

    @Column(name = "title_", length = 500)
    private String title;

    @Column(name = "summary_", length = 1000)
    private String summary;

    @Column(name = "content_", columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags_", columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(name = "org_id", length = 64)
    private String orgId;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
        if (tags == null) {
            tags = new ArrayList<>();
        }
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}