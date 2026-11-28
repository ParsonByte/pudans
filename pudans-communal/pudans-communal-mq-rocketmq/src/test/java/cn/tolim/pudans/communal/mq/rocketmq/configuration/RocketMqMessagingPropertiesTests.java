package cn.tolim.pudans.communal.mq.rocketmq.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocketMqMessagingPropertiesTests {

    private final RocketMqMessagingProperties properties = new RocketMqMessagingProperties();

    @Test
    void usesLocalDefaults() {
        assertThat(properties.getNameServer()).isEqualTo("127.0.0.1:9876");
        assertThat(properties.getProducerGroup()).isEqualTo("pudans-producer");
        assertThat(properties.getSendTimeoutMs()).isEqualTo(3000);
    }

    @Test
    void rejectsBlankNameServer() {
        assertThatThrownBy(() -> properties.setNameServer(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveSendTimeout() {
        assertThatThrownBy(() -> properties.setSendTimeoutMs(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}