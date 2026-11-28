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
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pudans_search_index",
        uniqueConstraints = @UniqueConstraint(name = "uk_search_index_code",
                columnNames = {"app_code", "index_code"}),
        indexes = @Index(name = "idx_search_index_app_code", columnList = "app_code"))
public class SearchIndexEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id_", nullable = false, length = 64)
    private String id;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "index_code", nullable = false, length = 100)
    private String indexCode;

    @Column(name = "name_", length = 200)
    private String name;

    @Column(name = "description_", length = 500)
    private String description;

    @Column(name = "enabled_", nullable = false)
    private Boolean enabled = Boolean.TRUE;

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
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}