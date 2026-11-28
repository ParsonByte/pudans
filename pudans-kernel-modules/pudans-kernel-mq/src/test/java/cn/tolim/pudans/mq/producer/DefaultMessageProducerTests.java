package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.mq.config.MessageCenterProperties;
import cn.tolim.pudans.mq.entity.MessageEntity;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.model.MessagePublishRequest;
import cn.tolim.pudans.mq.model.MessagePublishResult;
import cn.tolim.pudans.mq.repository.MessageRepository;
import cn.tolim.pudans.mq.service.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMessageProducerTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private MessageRepository messageRepository;

    private ApplicationEventPublisher eventPublisher;

    private DefaultMessageProducer producer;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        producer = new DefaultMessageProducer(messageRepository, new MessageMapper(),
                new MessageCenterProperties(), eventPublisher, CLOCK);
        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId("message-1");
            }
            return message;
        });
    }

    @Test
    void persistsPendingMessageAndAnnouncesIt() {
        when(messageRepository.findByBizKey("biz-1")).thenReturn(Optional.empty());
        MessagePublishRequest request = new MessagePublishRequest("order.created", "OrderCreated",
                "order", "biz-1", Map.of("orderId", 42), Map.of("traceId", "trace-1"), 3);

        MessagePublishResult result = producer.publish(request);

        assertThat(result.id()).isEqualTo("message-1");
        assertThat(result.status()).isEqualTo(MessageStatus.PENDING);
        assertThat(result.duplicate()).isFalse();
        assertThat(result.createTime()).isEqualTo(NOW);

        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(captor.capture());
        MessageEntity saved = captor.getValue();
        assertThat(saved.getBizKey()).isEqualTo("biz-1");
        assertThat(saved.getTopic()).isEqualTo("order.created");
        assertThat(saved.getEventType()).isEqualTo("OrderCreated");
        assertThat(saved.getProducer()).isEqualTo("order");
        assertThat(saved.getStatus()).isEqualTo(MessageStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getMaxAttempts()).isEqualTo(3);
        assertThat(saved.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(saved.getPayload()).contains("\"orderId\":42");
        assertThat(saved.getHeaders()).contains("\"traceId\":\"trace-1\"");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(MessageQueuedEvent.class);
        MessageQueuedEvent event = (MessageQueuedEvent) eventCaptor.getValue();
        assertThat(event.envelope().id()).isEqualTo("message-1");
        assertThat(event.envelope().topic()).isEqualTo("order.created");
    }

    @Test
    void returnsExistingMessageForDuplicateBizKey() {
        MessageEntity existing = new MessageEntity();
        existing.setId("existing-1");
        existing.setStatus(MessageStatus.CONSUMED);
        existing.setCreateTime(NOW.minusHours(1));
        when(messageRepository.findByBizKey("biz-1")).thenReturn(Optional.of(existing));

        MessagePublishResult result = producer.publish(new MessagePublishRequest("order.created",
                null, null, "biz-1", Map.of("orderId", 42), Map.of(), null));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.id()).isEqualTo("existing-1");
        assertThat(result.status()).isEqualTo(MessageStatus.CONSUMED);
        verify(messageRepository, never()).save(any(MessageEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void appliesDefaultProducerAndRetryBudget() {
        MessagePublishRequest request = new MessagePublishRequest("order.created", null, null,
                null, "payload", null, null);

        MessagePublishResult result = producer.publish(request);

        assertThat(result.status()).isEqualTo(MessageStatus.PENDING);
        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getProducer()).isEqualTo("system");
        assertThat(captor.getValue().getMaxAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getHeaders()).isNull();
    }

    @Test
    void rejectsInvalidTopic() {
        MessagePublishRequest request = new MessagePublishRequest("bad topic!", null, null,
                null, Map.of(), Map.of(), null);

        assertThatThrownBy(() -> producer.publish(request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(400));
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }

    @Test
    void rejectsOversizedPayload() {
        MessageCenterProperties properties = new MessageCenterProperties();
        properties.setMaxPayloadBytes(4);
        DefaultMessageProducer constrainedProducer = new DefaultMessageProducer(messageRepository,
                new MessageMapper(), properties, eventPublisher, CLOCK);

        assertThatThrownBy(() -> constrainedProducer.publish(new MessagePublishRequest(
                "order.created", null, null, null, "payload", Map.of(), null)))
                .isInstanceOf(ApiException.class);
        verify(messageRepository, never()).save(any(MessageEntity.class));
    }
}