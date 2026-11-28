package cn.tolim.pudans.notification.entity;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

@Getter
@Setter
@Entity
@Table(name = "pudans_notification_provider",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_provider_code",
                columnNames = "provider_code"))
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class NotificationProviderEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 7602221852318765066L;

    // Stable provider code referenced by send requests.
    @Column(name = "provider_code", nullable = false, length = 100)
    private String code;

    @Column(name = "provider_name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_", nullable = false, length = 32)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType provider;

    @Column(name = "priority_", nullable = false)
    private Integer priority;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled;

    // Serialized provider settings encrypted with spissatus4j before storage.
    @Column(name = "configuration_", columnDefinition = "TEXT")
    private String configuration;
}