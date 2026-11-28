package cn.tolim.pudans.mq.service;

import cn.tolim.pudans.mq.config.MessageCenterProperties;
import cn.tolim.pudans.mq.consumer.MessageConsumer;
import cn.tolim.pudans.mq.consumer.MessageConsumerRegistry;
import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.entity.MessageDeliveryEntity;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.enums.DeliveryStatus;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.repository.DeadLetterRepository;
import cn.tolim.pudans.mq.repository.MessageDeliveryRepository;
import cn.tolim.pudans.mq.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDeliveryServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private MessageRepository messageRepository;

    private MessageDeliveryRepository deliveryRepository;

    private DeadLetterRepository deadLetterRepository;

    private MessageConsumerRegistry consumerRegistry;

    private MessageDeliveryService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        deliveryRepository = mock(MessageDeliveryRepository.class);
        deadLetterRepository = mock(DeadLetterRepository.class);
        consumerRegistry = mock(MessageConsumerRegistry.class);
        MessageCenterProperties properties = new MessageCenterProperties();
        properties.setRetryInitialDelayMs(1000);
        properties.setRetryMaxDelayMs(60000);
        properties.setRetryMultiplier(2.0);
        RetryPolicy retryPolicy = new RetryPolicy(properties.getRetryInitialDelayMs(),
                properties.getRetryMaxDelayMs(), properties.getRetryMultiplier());
        service = new MessageDeliveryService(messageRepository, deliveryRepository,
                deadLetterRepository, consumerRegistry, retryPolicy, properties,
                new MessageMapper(), CLOCK);

        when(messageRepository.save(any(MessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.save(any(MessageDeliveryEntity.class))).thenAnswer(invocation -> {
            MessageDeliveryEntity delivery = invocation.getArgument(0);
            if (delivery.getId() == null) {
                delivery.setId("delivery-1");
            }
            return delivery;
        });
        when(deliveryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void consumesMessageWhenEveryConsumerSucceeds() {
        MessageEntity message = message(MessageStatus.DISPATCHING, 1, 3);
        MessageConsumer consumer = consumer("billing");
        givenClaimed(message, List.of(consumer));
        when(deliveryRepository.findByMessageIdAndConsumerGroup("message-1", "billing"))
                .thenReturn(Optional.empty());

        DispatchOutcome outcome = service.dispatch("message-1");

        assertThat(outcome).isEqualTo(DispatchOutcome.CONSUMED);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.CONSUMED);
        assertThat(message.getConsumedAt()).isEqualTo(NOW);
        assertThat(message.getNextAttemptAt()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MessageDeliveryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        MessageDeliveryEntity delivery = captor.getValue().getFirst();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONSUMED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getMaxAttempts()).isEqualTo(3);
        verify(deadLetterRepository, never()).saveAll(anyList());
    }

    @Test
    void retriesMessageWhenConsumerFailsWithinBudget() throws Exception {
        MessageEntity message = message(MessageStatus.DISPATCHING, 1, 3);
        MessageConsumer consumer = consumer("billing");
        doThrow(new RuntimeException("boom")).when(consumer).onMessage(any());
        givenClaimed(message, List.of(consumer));
        when(deliveryRepository.findByMessageIdAndConsumerGroup("message-1", "billing"))
                .thenReturn(Optional.empty());

        DispatchOutcome outcome = service.dispatch("message-1");

        assertThat(outcome).isEqualTo(DispatchOutcome.RETRYING);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.RETRYING);
        assertThat(message.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(message.getLastError()).isEqualTo("boom");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MessageDeliveryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        MessageDeliveryEntity delivery = captor.getValue().getFirst();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(delivery.getLastError()).isEqualTo("boom");
        verify(deadLetterRepository, never()).saveAll(anyList());
    }

    @Test
    void parksMessageAndCreatesDeadLetterWhenBudgetExhausted() throws Exception {
        MessageEntity message = message(MessageStatus.DISPATCHING, 3, 3);
        MessageConsumer consumer = consumer("billing");
        doThrow(new RuntimeException("boom")).when(consumer).onMessage(any());
        givenClaimed(message, List.of(consumer));
        when(deliveryRepository.findByMessageIdAndConsumerGroup("message-1", "billing"))
                .thenReturn(Optional.empty());

        DispatchOutcome outcome = service.dispatch("message-1");

        assertThat(outcome).isEqualTo(DispatchOutcome.DEAD_LETTER);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.DEAD_LETTER);
        assertThat(message.getDeadLetterAt()).isEqualTo(NOW);
        assertThat(message.getNextAttemptAt()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeadLetterEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(deadLetterRepository).saveAll(captor.capture());
        DeadLetterEntity deadLetter = captor.getValue().getFirst();
        assertThat(deadLetter.getStatus()).isEqualTo(DeadLetterStatus.PENDING);
        assertThat(deadLetter.getMessageId()).isEqualTo("message-1");
        assertThat(deadLetter.getConsumerGroup()).isEqualTo("billing");
        assertThat(deadLetter.getTopic()).isEqualTo("order.created");
        assertThat(deadLetter.getLastError()).isEqualTo("boom");
        assertThat(deadLetter.getDeadLetteredAt()).isEqualTo(NOW);
    }

    @Test
    void movesExistingRetryingDeliveryToConsumedOnSuccess() {
        MessageEntity message = message(MessageStatus.DISPATCHING, 2, 3);
        MessageConsumer consumer = consumer("billing");
        MessageDeliveryEntity delivery = new MessageDeliveryEntity();
        delivery.setId("delivery-1");
        delivery.setMessageId("message-1");
        delivery.setConsumerGroup("billing");
        delivery.setStatus(DeliveryStatus.RETRYING);
        delivery.setAttempts(1);
        delivery.setMaxAttempts(3);
        givenClaimed(message, List.of(consumer));
        when(deliveryRepository.findByMessageIdAndConsumerGroup("message-1", "billing"))
                .thenReturn(Optional.of(delivery));

        DispatchOutcome outcome = service.dispatch("message-1");

        assertThat(outcome).isEqualTo(DispatchOutcome.CONSUMED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONSUMED);
        assertThat(delivery.getAttempts()).isEqualTo(2);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.CONSUMED);
    }

    @Test
    void marksMessageDeliveredWhenNoConsumerIsRegistered() {
        MessageEntity message = message(MessageStatus.DISPATCHING, 1, 3);
        givenClaimed(message, List.of());

        DispatchOutcome outcome = service.dispatch("message-1");

        assertThat(outcome).isEqualTo(DispatchOutcome.DELIVERED);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.DELIVERED);
        assertThat(message.getDeliveredAt()).isEqualTo(NOW);
        verify(deliveryRepository, never()).saveAll(anyList());
    }

    @Test
    void skipsMessageAlreadyClaimedByAnotherDispatcher() {
        when(messageRepository.claim(eq("message-1"), eq(MessageStatus.DISPATCHING),
                eq(MessageStatus.PENDING), eq(MessageStatus.RETRYING), any(LocalDateTime.class)))
                .thenReturn(0);

        DispatchOutcome outcome = service.dispatch("message-1");

        assertThat(outcome).isEqualTo(DispatchOutcome.SKIPPED);
        verify(messageRepository, never()).findById(anyString());
    }

    private void givenClaimed(MessageEntity message, List<MessageConsumer> consumers) {
        when(messageRepository.claim(eq(message.getId()), eq(MessageStatus.DISPATCHING),
                eq(MessageStatus.PENDING), eq(MessageStatus.RETRYING), any(LocalDateTime.class)))
                .thenReturn(1);
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(consumerRegistry.consumersFor(message.getTopic())).thenReturn(consumers);
    }

    private MessageEntity message(MessageStatus status, int attempts, int maxAttempts) {
        MessageEntity message = new MessageEntity();
        message.setId("message-1");
        message.setTopic("order.created");
        message.setEventType("OrderCreated");
        message.setProducer("order");
        message.setPayload("{\"orderId\":42}");
        message.setHeaders("{\"traceId\":\"t-1\"}");
        message.setStatus(status);
        message.setAttempts(attempts);
        message.setMaxAttempts(maxAttempts);
        message.setCreateTime(NOW);
        return message;
    }

    private MessageConsumer consumer(String group) {
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(consumer.consumerGroup()).thenReturn(group);
        when(consumer.topics()).thenReturn(Set.of("order.created"));
        return consumer;
    }
}