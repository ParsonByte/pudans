package cn.tolim.pudans.mq.dispatch;

import cn.tolim.pudans.communal.mq.consumer.MessageConsumer;
import cn.tolim.pudans.communal.mq.consumer.MessageSubscription;
import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.mq.model.MessageEnvelope;
import cn.tolim.pudans.mq.service.MessageDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RocketMqAnnouncementSubscriberTests {

    @Test
    void dispatchesAnnouncedMessageId() throws Exception {
        MessageConsumer consumer = mock(MessageConsumer.class);
        MessageConsumer.SubscriptionHandle handle = mock(MessageConsumer.SubscriptionHandle.class);
        when(consumer.subscribe(any())).thenReturn(handle);
        MessageDeliveryService deliveryService = mock(MessageDeliveryService.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        RocketMqAnnouncementSubscriber subscriber = new RocketMqAnnouncementSubscriber(consumer,
                deliveryService, jsonMapper, "pudans-message-announcement", "pudans-mq-announcement");
        subscriber.start();
        ArgumentCaptor<MessageSubscription> captor = ArgumentCaptor.forClass(MessageSubscription.class);
        verify(consumer).subscribe(captor.capture());
        MessageSubscription subscription = captor.getValue();
        assertThat(subscription.topic()).isEqualTo("pudans-message-announcement");
        assertThat(subscription.consumerGroup()).isEqualTo("pudans-mq-announcement");
        MessageEnvelope envelope = new MessageEnvelope("message-1", "biz-1", "iam.user.created",
                "UserCreated", "iam", "{}", Map.of(), 0, LocalDateTime.now());
        Message message = new Message("pudans-message-announcement", "ANNOUNCEMENT", "message-1",
                jsonMapper.writeValueAsString(envelope), Map.of(), Instant.now());
        subscription.listener().onMessage(message);
        verify(deliveryService).dispatch("message-1");
        subscriber.stop();
        verify(handle).close();
    }

    @Test
    void ignoresMalformedAnnouncement() throws Exception {
        MessageConsumer consumer = mock(MessageConsumer.class);
        when(consumer.subscribe(any())).thenReturn(mock(MessageConsumer.SubscriptionHandle.class));
        MessageDeliveryService deliveryService = mock(MessageDeliveryService.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        RocketMqAnnouncementSubscriber subscriber = new RocketMqAnnouncementSubscriber(consumer,
                deliveryService, jsonMapper, "pudans-message-announcement", "pudans-mq-announcement");
        subscriber.start();
        ArgumentCaptor<MessageSubscription> captor = ArgumentCaptor.forClass(MessageSubscription.class);
        verify(consumer).subscribe(captor.capture());
        Message message = new Message("pudans-message-announcement", "ANNOUNCEMENT", "message-1",
                "not-json", Map.of(), Instant.now());
        captor.getValue().listener().onMessage(message);
        verifyNoInteractions(deliveryService);
    }
}