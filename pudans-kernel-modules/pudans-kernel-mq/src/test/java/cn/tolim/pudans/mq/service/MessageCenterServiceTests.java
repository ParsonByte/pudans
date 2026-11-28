package cn.tolim.pudans.mq.service;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.mq.consumer.MessageConsumerRegistry;
import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.enums.DeliveryStatus;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.model.MessageResponse;
import cn.tolim.pudans.mq.producer.MessageQueuedEvent;
import cn.tolim.pudans.mq.repository.DeadLetterRepository;
import cn.tolim.pudans.mq.repository.MessageDeliveryRepository;
import cn.tolim.pudans.mq.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageCenterServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private MessageRepository messageRepository;

    private MessageDeliveryRepository deliveryRepository;

    private DeadLetterRepository deadLetterRepository;

    private ApplicationEventPublisher eventPublisher;

    private MessageCenterService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        deliveryRepository = mock(MessageDeliveryRepository.class);
        deadLetterRepository = mock(DeadLetterRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new MessageCenterService(messageRepository, deliveryRepository,
                deadLetterRepository, mock(MessageConsumerRegistry.class), new MessageMapper(),
                eventPublisher, CLOCK);
        when(messageRepository.save(any(MessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.save(any(MessageDeliveryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterRepository.save(any(DeadLetterEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void retryFromDeadLetterResetsMessageDeliveriesAndLetters() {
        MessageEntity message = message(MessageStatus.DEAD_LETTER, 3, 3);
        MessageDeliveryEntity delivery = delivery(DeliveryStatus.DEAD_LETTER, 3);
        DeadLetterEntity deadLetter = deadLetter(DeadLetterStatus.PENDING);
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(deliveryRepository.findByMessageId("message-1")).thenReturn(List.of(delivery));
        when(deadLetterRepository.findByMessageIdAndStatus("message-1", DeadLetterStatus.PENDING))
                .thenReturn(List.of(deadLetter));

        MessageResponse response = service.retryMessage("message-1");

        assertThat(response.status()).isEqualTo(MessageStatus.PENDING);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.PENDING);
        assertThat(message.getAttempts()).isZero();
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(message.getDeadLetterAt()).isNull();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(deadLetter.getStatus()).isEqualTo(DeadLetterStatus.REPLAYED);
        assertThat(deadLetter.getReplayedAt()).isEqualTo(NOW);
        verify(eventPublisher).publishEvent(any(MessageQueuedEvent.class));
    }

    @Test
    void retryOfRetryingMessageMakesItDueImmediately() {
        MessageEntity message = message(MessageStatus.RETRYING, 2, 3);
        message.setNextAttemptAt(NOW.plusMinutes(10));
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));

        MessageResponse response = service.retryMessage("message-1");

        assertThat(response.status()).isEqualTo(MessageStatus.RETRYING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsRetryOfTerminalMessage() {
        MessageEntity message = message(MessageStatus.CONSUMED, 1, 3);
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.retryMessage("message-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(409));
    }

    @Test
    void cancelMovesMessageDeliveriesAndDeadLettersToTerminalStates() {
        MessageEntity message = message(MessageStatus.RETRYING, 1, 3);
        MessageDeliveryEntity delivery = delivery(DeliveryStatus.RETRYING, 1);
        DeadLetterEntity deadLetter = deadLetter(DeadLetterStatus.PENDING);
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(deliveryRepository.findByMessageId("message-1")).thenReturn(List.of(delivery));
        when(deadLetterRepository.findByMessageIdAndStatus("message-1", DeadLetterStatus.PENDING))
                .thenReturn(List.of(deadLetter));

        MessageResponse response = service.cancelMessage("message-1");

        assertThat(response.status()).isEqualTo(MessageStatus.CANCELLED);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.CANCELLED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(deadLetter.getStatus()).isEqualTo(DeadLetterStatus.DISCARDED);
        assertThat(deadLetter.getDiscardedAt()).isEqualTo(NOW);
    }

    @Test
    void replayDeadLetterResetsOnlyTheFailedDelivery() {
        MessageEntity message = message(MessageStatus.RETRYING, 1, 3);
        message.setNextAttemptAt(NOW.plusMinutes(10));
        MessageDeliveryEntity delivery = delivery(DeliveryStatus.RETRYING, 1);
        DeadLetterEntity deadLetter = deadLetter(DeadLetterStatus.PENDING);
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(deliveryRepository.findById("delivery-1")).thenReturn(Optional.of(delivery));

        service.replayDeadLetter("dead-letter-1");

        assertThat(message.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(deadLetter.getStatus()).isEqualTo(DeadLetterStatus.REPLAYED);
        verify(eventPublisher).publishEvent(any(MessageQueuedEvent.class));
    }

    @Test
    void rejectsReplayOfResolvedDeadLetter() {
        DeadLetterEntity deadLetter = deadLetter(DeadLetterStatus.REPLAYED);
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));

        assertThatThrownBy(() -> service.replayDeadLetter("dead-letter-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(409));
    }

    @Test
    void discardMarksDeadLetterDiscarded() {
        DeadLetterEntity deadLetter = deadLetter(DeadLetterStatus.PENDING);
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));

        service.discardDeadLetter("dead-letter-1");

        assertThat(deadLetter.getStatus()).isEqualTo(DeadLetterStatus.DISCARDED);
        assertThat(deadLetter.getDiscardedAt()).isEqualTo(NOW);
    }

    @Test
    void reportsMissingMessage() {
        when(messageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessage("missing"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(404));
    }

    private MessageEntity message(MessageStatus status, int attempts, int maxAttempts) {
        MessageEntity message = new MessageEntity();
        message.setId("message-1");
        message.setTopic("order.created");
        message.setEventType("OrderCreated");
        message.setProducer("order");
        message.setPayload("{\"orderId\":42}");
        message.setStatus(status);
        message.setAttempts(attempts);
        message.setMaxAttempts(maxAttempts);
        message.setCreateTime(NOW);
        return message;
    }

    private MessageDeliveryEntity delivery(DeliveryStatus status, int attempts) {
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setId("delivery-1");
        delivery.setMessageId("message-1");
        delivery.setConsumerGroup("billing");
        delivery.setTopic("order.created");
        delivery.setStatus(status);
        delivery.setAttempts(attempts);
        delivery.setMaxAttempts(3);
        return delivery;
    }

    private DeadLetterEntity deadLetter(DeadLetterStatus status) {
        DeadLetterEntity deadLetter = new DeadLetterEntity();
        deadLetter.setId("dead-letter-1");
        deadLetter.setMessageId("message-1");
        deadLetter.setDeliveryId("delivery-1");
        deadLetter.setConsumerGroup("billing");
        deadLetter.setTopic("order.created");
        deadLetter.setPayload("{\"orderId\":42}");
        deadLetter.setAttempts(3);
        deadLetter.setStatus(status);
        deadLetter.setDeadLetteredAt(NOW.minusMinutes(5));
        return deadLetter;
    }
}