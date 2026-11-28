package cn.tolim.pudans.mq.producer;

import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.producer.MessageProducer;
import cn.tolim.pudans.mq.model.MessageEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RocketMqMessageTransportTests {

    @Test
    void announcesEnvelopeWithBusinessHeaders() {
        MessageProducer producer = mock(MessageProducer.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        RocketMqMessageTransport transport = new RocketMqMessageTransport(producer,
                "pudans-message-announcement", jsonMapper);
        MessageEnvelope envelope = new MessageEnvelope("message-1", "biz-1", "iam.user.created",
                "UserCreated", "iam", "{\"id\":1}", Map.of("traceId", "t-1"), 0,
                LocalDateTime.of(2026, 1, 1, 10, 0));
        transport.announce(envelope);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(captor.capture());
        Message message = captor.getValue();
        assertThat(message.topic()).isEqualTo("pudans-message-announcement");
        assertThat(message.tag()).isEqualTo(RocketMqMessageTransport.ANNOUNCEMENT_TAG);
        assertThat(message.key()).isEqualTo("message-1");
        assertThat(message.headers()).containsEntry("topic", "iam.user.created")
                .containsEntry("eventType", "UserCreated")
                .containsEntry("producer", "iam")
                .containsEntry("bizKey", "biz-1");
        assertThat(message.payload()).contains("\"id\":\"message-1\"");
        assertThat(transport.name()).isEqualTo("rocketmq");
    }
}