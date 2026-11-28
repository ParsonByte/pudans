package cn.tolim.pudans.communal.mq.rocketmq.broker;

import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.rocketmq.configuration.RocketMqMessagingProperties;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocketMqMessageBrokerTests {

    @Test
    void mapsMessageToRocketMessage() {
        Message message = new Message("stage3-topic", "ANNOUNCEMENT", "key-1", "{\"id\":1}",
                Map.of("eventType", "created"), Instant.now());
        org.apache.rocketmq.common.message.Message rocketMessage =
                RocketMqMessageBroker.toRocketMessage(message);
        assertThat(rocketMessage.getTopic()).isEqualTo("stage3-topic");
        assertThat(rocketMessage.getTags()).isEqualTo("ANNOUNCEMENT");
        assertThat(rocketMessage.getKeys()).isEqualTo("key-1");
        assertThat(new String(rocketMessage.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"id\":1}");
        assertThat(rocketMessage.getUserProperty("pudans_eventType")).isEqualTo("created");
    }

    @Test
    void mapsMessageExtToMessage() {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic("stage3-topic");
        messageExt.setTags("ANNOUNCEMENT");
        messageExt.setKeys("key-1");
        messageExt.setBody("{\"id\":1}".getBytes(StandardCharsets.UTF_8));
        messageExt.setBornTimestamp(1234567890L);
        messageExt.putUserProperty("pudans_eventType", "created");
        messageExt.putUserProperty("traceId", "ignored");
        Message message = RocketMqMessageBroker.toMessage(messageExt);
        assertThat(message.topic()).isEqualTo("stage3-topic");
        assertThat(message.tag()).isEqualTo("ANNOUNCEMENT");
        assertThat(message.key()).isEqualTo("key-1");
        assertThat(message.payload()).isEqualTo("{\"id\":1}");
        assertThat(message.headers()).containsEntry("eventType", "created");
        assertThat(message.headers()).doesNotContainKey("traceId");
        assertThat(message.createdAt()).isEqualTo(Instant.ofEpochMilli(1234567890L));
    }

    @Test
    void rejectsInvertedConsumerThreadBounds() {
        RocketMqMessagingProperties properties = new RocketMqMessagingProperties();
        properties.setConsumerThreadMin(10);
        properties.setConsumerThreadMax(5);
        assertThatThrownBy(() -> new RocketMqMessageBroker(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHalfConfiguredAcl() {
        RocketMqMessagingProperties properties = new RocketMqMessagingProperties();
        properties.setAccessKey("access-key");
        assertThatThrownBy(() -> new RocketMqMessageBroker(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsCompleteAclCredentials() {
        RocketMqMessagingProperties properties = new RocketMqMessagingProperties();
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");
        assertThat(new RocketMqMessageBroker(properties).name()).isEqualTo("rocketmq");
    }
}