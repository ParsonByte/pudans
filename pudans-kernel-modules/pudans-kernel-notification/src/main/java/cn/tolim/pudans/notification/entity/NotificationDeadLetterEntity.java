package cn.tolim.pudans.notification.entity;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

@Getter
@Setter
@Entity
@Table(name = "pudans_notification_dead_letter",
        indexes = {
                @Index(name = "idx_notification_dead_letter_record", columnList = "record_id"),
                @Index(name = "idx_notification_dead_letter_replayed", columnList = "is_replayed")
        })
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class NotificationDeadLetterEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 4195374248661104987L;

    // Original send record identifier.
    @Column(name = "record_id", nullable = false, length = 64)
    private String recordId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "template_code", length = 100)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_", nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(name = "provider_code", length = 100)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", length = 32)
    private ProviderType providerType;

    @Column(name = "recipient_", nullable = false, length = 500)
    private String recipient;

    @Column(name = "subject_", columnDefinition = "TEXT")
    private String subject;

    @Column(name = "content_", columnDefinition = "TEXT")
    private String content;

    @Column(name = "parameters_", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "template_parameters_", columnDefinition = "TEXT")
    private String templateParameters;

    @Column(name = "attempts_", nullable = false)
    private Integer attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "is_replayed", nullable = false)
    private Boolean replayed;

    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;
}