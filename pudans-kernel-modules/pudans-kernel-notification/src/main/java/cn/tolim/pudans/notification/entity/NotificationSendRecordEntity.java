package cn.tolim.pudans.notification.entity;

import cn.tolim.pudans.iam.infra.repository.SoftDeleteConverter;
import cn.tolim.pudans.iam.infra.repository.base.BaseEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.enums.SendStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.io.Serial;
import java.time.LocalDateTime;

import static cn.tolim.pudans.iam.infra.repository.base.BaseEntity.IS_DELETED_COLUMN;

@Getter
@Setter
@Entity
@Table(name = "pudans_notification_send_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_send_request_id",
                columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_notification_send_status_retry",
                        columnList = "status_,next_retry_at"),
                @Index(name = "idx_notification_send_recipient", columnList = "recipient_"),
                @Index(name = "idx_notification_send_channel", columnList = "channel_")
        })
@SoftDelete(columnName = IS_DELETED_COLUMN, converter = SoftDeleteConverter.class)
public class NotificationSendRecordEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = -2410332438938557054L;

    // Caller supplied idempotency key.
    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "template_code", length = 100)
    private String templateCode;

    // Provider-side template identifier used for this send.
    @Column(name = "provider_template_code", length = 100)
    private String providerTemplateCode;

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

    // Template parameters serialized as JSON, used for provider template sends and retries.
    @Column(name = "parameters_", columnDefinition = "TEXT")
    private String parameters;

    // Ordered template parameters serialized as JSON for providers that require a list.
    @Column(name = "template_parameters_", columnDefinition = "TEXT")
    private String templateParameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_", nullable = false, length = 32)
    private SendStatus status;

    @Column(name = "attempts_", nullable = false)
    private Integer attempts;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}