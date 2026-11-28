package cn.tolim.pudans.mq.entity;

import cn.tolim.pudans.mq.enums.DeliveryStatus;
import cn.tolim.pudans.mq.exception.MessageStateTransitionException;
import cn.tolim.pudans.mq.util.MessageTexts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pudans_mq_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uk_pudans_mq_delivery_message_group",
                columnNames = {"message_id", "consumer_group"}),
        indexes = {
                @Index(name = "idx_pudans_mq_delivery_message", columnList = "message_id"),
                @Index(name = "idx_pudans_mq_delivery_status_next", columnList = "status_,next_attempt_at")
        })
public class MessageDeliveryEntity extends MqBaseEntity {

    @Serial
    private static final long serialVersionUID = -3372194570305788361L;

    // Owning message id
    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    // Consumer group that owns this delivery
    @Column(name = "consumer_group", nullable = false, length = 200)
    private String consumerGroup;

    // Topic snapshot for querying
    @Column(name = "topic_", length = 128)
    private String topic;

    // Delivery state machine state
    @Column(name = "status_", nullable = false, length = 32)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    // Number of consumer invocations
    @Column(name = "attempts_", nullable = false)
    private int attempts;

    // Consumer invocation budget
    @Column(name = "max_attempts_", nullable = false)
    private int maxAttempts = 5;

    // Earliest time of the next consumer invocation
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    // Time of the last consumer invocation
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    // Time the consumer acknowledged the message
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    // Last consumer error
    @Column(name = "last_error", length = 4000)
    private String lastError;

    @Version
    @Column(name = "version_")
    private Long version;

    public void transitionTo(DeliveryStatus target) {
        if (status == null) {
            status = target;
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new MessageStateTransitionException("delivery", getId(), status, target);
        }
        status = target;
    }

    public void markDelivering(LocalDateTime now) {
        transitionTo(DeliveryStatus.DELIVERING);
        attempts++;
        lastAttemptAt = now;
        nextAttemptAt = null;
    }

    public void markConsumed(LocalDateTime now) {
        transitionTo(DeliveryStatus.CONSUMED);
        consumedAt = now;
        nextAttemptAt = null;
        lastError = null;
    }

    public void recordFailure(String error, LocalDateTime now) {
        lastError = MessageTexts.truncate(error, 4000);
        lastAttemptAt = now;
        if (attempts >= maxAttempts) {
            transitionTo(DeliveryStatus.DEAD_LETTER);
            nextAttemptAt = null;
        } else {
            transitionTo(DeliveryStatus.RETRYING);
        }
    }

    public void markDeadLetter(LocalDateTime now) {
        if (status != DeliveryStatus.DEAD_LETTER) {
            transitionTo(DeliveryStatus.DEAD_LETTER);
        }
        lastAttemptAt = now;
        nextAttemptAt = null;
    }

    public void markCancelled(LocalDateTime now) {
        if (status != DeliveryStatus.CANCELLED) {
            transitionTo(DeliveryStatus.CANCELLED);
        }
        nextAttemptAt = null;
    }

    public void resetForReplay(LocalDateTime now) {
        if (status == DeliveryStatus.CONSUMED || status == DeliveryStatus.CANCELLED) {
            return;
        }
        if (status == DeliveryStatus.DEAD_LETTER || status == DeliveryStatus.RETRYING) {
            transitionTo(DeliveryStatus.PENDING);
        }
        attempts = 0;
        nextAttemptAt = now;
        lastError = null;
    }
}