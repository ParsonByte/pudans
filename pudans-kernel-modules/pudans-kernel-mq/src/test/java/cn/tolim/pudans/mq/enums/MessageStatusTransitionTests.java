package cn.tolim.pudans.mq.enums;

import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.exception.MessageStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageStatusTransitionTests {

    @Test
    void allowsExpectedMessageTransitions() {
        assertThat(MessageStatus.PENDING.canTransitionTo(MessageStatus.DISPATCHING)).isTrue();
        assertThat(MessageStatus.PENDING.canTransitionTo(MessageStatus.CANCELLED)).isTrue();
        assertThat(MessageStatus.DISPATCHING.canTransitionTo(MessageStatus.CONSUMED)).isTrue();
        assertThat(MessageStatus.DISPATCHING.canTransitionTo(MessageStatus.RETRYING)).isTrue();
        assertThat(MessageStatus.DISPATCHING.canTransitionTo(MessageStatus.DEAD_LETTER)).isTrue();
        assertThat(MessageStatus.RETRYING.canTransitionTo(MessageStatus.DISPATCHING)).isTrue();
        assertThat(MessageStatus.DEAD_LETTER.canTransitionTo(MessageStatus.PENDING)).isTrue();
        assertThat(MessageStatus.DEAD_LETTER.canTransitionTo(MessageStatus.CANCELLED)).isTrue();
    }

    @Test
    void rejectsInvalidMessageTransitions() {
        assertThat(MessageStatus.PENDING.canTransitionTo(MessageStatus.CONSUMED)).isFalse();
        assertThat(MessageStatus.CONSUMED.canTransitionTo(MessageStatus.RETRYING)).isFalse();
        assertThat(MessageStatus.CANCELLED.canTransitionTo(MessageStatus.PENDING)).isFalse();
        assertThat(MessageStatus.DELIVERED.canTransitionTo(MessageStatus.CONSUMED)).isFalse();
        assertThat(MessageStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Test
    void reportsTerminalMessageStates() {
        assertThat(MessageStatus.CONSUMED.isTerminal()).isTrue();
        assertThat(MessageStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(MessageStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(MessageStatus.RETRYING.isTerminal()).isFalse();
        assertThat(MessageStatus.DEAD_LETTER.isTerminal()).isFalse();
    }

    @Test
    void entityRejectsInvalidTransition() {
        MessageEntity message = new MessageEntity();
        message.setId("message-1");
        message.setStatus(MessageStatus.CONSUMED);

        assertThatThrownBy(() -> message.transitionTo(MessageStatus.RETRYING))
                .isInstanceOf(MessageStateTransitionException.class)
                .hasMessageContaining("message-1")
                .hasMessageContaining("CONSUMED")
                .hasMessageContaining("RETRYING");
    }

    @Test
    void deliveryStateMachineRetriesThenDeadLetters() {
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setId("delivery-1");
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setMaxAttempts(2);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

        delivery.markDelivering(now);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERING);
        assertThat(delivery.getAttempts()).isEqualTo(1);

        delivery.recordFailure("first failure", now);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(delivery.getLastError()).isEqualTo("first failure");

        delivery.markDelivering(now);
        delivery.recordFailure("second failure", now);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DEAD_LETTER);
        assertThat(delivery.getAttempts()).isEqualTo(2);

        delivery.resetForReplay(now);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getNextAttemptAt()).isEqualTo(now);
        assertThat(delivery.getLastError()).isNull();
    }

    @Test
    void deliveryConsumptionIsTerminal() {
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setId("delivery-1");
        delivery.setStatus(DeliveryStatus.DELIVERING);
        delivery.markConsumed(LocalDateTime.now());

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONSUMED);
        assertThatThrownBy(() -> delivery.transitionTo(DeliveryStatus.RETRYING))
                .isInstanceOf(MessageStateTransitionException.class);
    }

    @Test
    void deadLetterLifecycleIsOneWay() {
        assertThat(DeadLetterStatus.PENDING.canTransitionTo(DeadLetterStatus.REPLAYED)).isTrue();
        assertThat(DeadLetterStatus.PENDING.canTransitionTo(DeadLetterStatus.DISCARDED)).isTrue();
        assertThat(DeadLetterStatus.REPLAYED.canTransitionTo(DeadLetterStatus.DISCARDED)).isFalse();
        assertThat(DeadLetterStatus.DISCARDED.canTransitionTo(DeadLetterStatus.REPLAYED)).isFalse();
    }
}