package cn.tolim.pudans.mq.entity;

import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.exception.MessageStateTransitionException;
import cn.tolim.pudans.mq.util.MessageTexts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Table(name = "pudans_mq_dead_letter",
        indexes = {
                @Index(name = "idx_pudans_mq_dead_letter_message", columnList = "message_id"),
                @Index(name = "idx_pudans_mq_dead_letter_status", columnList = "status_"),
                @Index(name = "idx_pudans_mq_dead_letter_topic", columnList = "topic_")
        })
public class DeadLetterEntity extends MqBaseEntity {

    @Serial
    private static final long serialVersionUID = 6228404561916233187L;

    // Owning message id
    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    // Failed delivery id
    @Column(name = "delivery_id", length = 64)
    private String deliveryId;

    // Topic snapshot
    @Column(name = "topic_", nullable = false, length = 128)
    private String topic;

    // Event type snapshot
    @Column(name = "event_type", length = 128)
    private String eventType;

    // Producer snapshot
    @Column(name = "producer_", length = 100)
    private String producer;

    // Consumer group that failed
    @Column(name = "consumer_group", length = 200)
    private String consumerGroup;

    // Raw JSON payload snapshot
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_")
    private String payload;

    // Raw JSON headers snapshot
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers_")
    private String headers;

    // Attempts made before parking
    @Column(name = "attempts_", nullable = false)
    private int attempts;

    // Final error message
    @Column(name = "last_error", length = 4000)
    private String lastError;

    // Dead letter lifecycle state
    @Column(name = "status_", nullable = false, length = 32)
    private DeadLetterStatus status = DeadLetterStatus.PENDING;

    // Time the message was parked
    @Column(name = "dead_lettered_at", nullable = false)
    private LocalDateTime deadLetteredAt;

    // Time the dead letter was replayed
    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    // Time the dead letter was discarded
    @Column(name = "discarded_at")
    private LocalDateTime discardedAt;

    // Compensation lifecycle state
    @Column(name = "compensation_status", nullable = false, length = 32)
    private CompensationStatus compensationStatus = CompensationStatus.NONE;

    // Time compensation succeeded
    @Column(name = "compensated_at")
    private LocalDateTime compensatedAt;

    // Last compensation error
    @Column(name = "compensation_error", length = 4000)
    private String compensationError;

    @Version
    @Column(name = "version_")
    private Long version;

    public void transitionTo(DeadLetterStatus target) {
        if (status == null) {
            status = target;
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new MessageStateTransitionException("dead letter", getId(), status, target);
        }
        status = target;
    }

    public void markReplayed(LocalDateTime now) {
        transitionTo(DeadLetterStatus.REPLAYED);
        replayedAt = now;
    }

    public void markDiscarded(LocalDateTime now) {
        transitionTo(DeadLetterStatus.DISCARDED);
        discardedAt = now;
    }

    public void markCompensated(LocalDateTime now) {
        if (compensationStatus == CompensationStatus.FAILED) {
            compensationStatus = CompensationStatus.COMPENSATED;
        } else {
            if (!compensationStatus.canTransitionTo(CompensationStatus.COMPENSATED)) {
                throw new MessageStateTransitionException("compensation", getId(), compensationStatus,
                        CompensationStatus.COMPENSATED);
            }
            compensationStatus = CompensationStatus.COMPENSATED;
        }
        compensatedAt = now;
        compensationError = null;
    }

    public void markCompensationFailed(String error, LocalDateTime now) {
        if (compensationStatus != CompensationStatus.FAILED) {
            if (!compensationStatus.canTransitionTo(CompensationStatus.FAILED)) {
                throw new MessageStateTransitionException("compensation", getId(), compensationStatus,
                        CompensationStatus.FAILED);
            }
            compensationStatus = CompensationStatus.FAILED;
        }
        compensatedAt = now;
        compensationError = MessageTexts.truncate(error, 4000);
    }
}