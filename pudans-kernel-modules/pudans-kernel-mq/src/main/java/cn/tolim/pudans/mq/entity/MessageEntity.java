package cn.tolim.pudans.mq.entity;

import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.exception.MessageStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pudans_mq_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_pudans_mq_message_biz_key", columnNames = "biz_key"),
        indexes = {
                @Index(name = "idx_pudans_mq_message_status_next", columnList = "status_,next_attempt_at"),
                @Index(name = "idx_pudans_mq_message_topic", columnList = "topic_"),
                @Index(name = "idx_pudans_mq_message_create_time", columnList = "create_time")
        })
public class MessageEntity extends MqBaseEntity {

    @Serial
    private static final long serialVersionUID = 4387201217137421862L;

    // Optional producer supplied idempotency key
    @Column(name = "biz_key", length = 128)
    private String bizKey;

    // Logical topic used for consumer routing
    @Column(name = "topic_", nullable = false, length = 128)
    private String topic;

    // Event type carried in the envelope
    @Column(name = "event_type", length = 128)
    private String eventType;

    // Producing application or service
    @Column(name = "producer_", length = 100)
    private String producer;

    // Raw JSON payload
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_", nullable = false)
    private String payload;

    // Raw JSON headers
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers_")
    private String headers;

    // Delivery state machine state
    @Column(name = "status_", nullable = false, length = 32)
    private MessageStatus status;

    // Number of dispatch rounds already claimed
    @Column(name = "attempts_", nullable = false)
    private int attempts;

    // Dispatch round budget
    @Column(name = "max_attempts_", nullable = false)
    private int maxAttempts = 5;

    // Earliest time of the next dispatch round
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    // Time of the last dispatch round
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    // Time the message was handed off without subscribers
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // Time all subscribers acknowledged the message
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    // Time the message was parked in the dead letter store
    @Column(name = "dead_letter_at")
    private LocalDateTime deadLetterAt;

    // Last delivery error
    @Column(name = "last_error", length = 4000)
    private String lastError;

    @Version
    @Column(name = "version_")
    private Long version;

    public void transitionTo(MessageStatus target) {
        if (status == null) {
            status = target;
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new MessageStateTransitionException("message", getId(), status, target);
        }
        status = target;
    }
}