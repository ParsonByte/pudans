package cn.tolim.pudans.communal.mq.rocketmq.configuration;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqMessagingAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RocketMqMessagingAutoConfiguration.class));

    @Test
    void registersRocketMqBrokerWhenSelected() {
        runner.withPropertyValues("pudans.messaging.broker=rocketmq",
                        "pudans.messaging.rocketmq.name-server=127.0.0.1:9876",
                        "pudans.messaging.rocketmq.producer-group=stage3-producer")
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageBroker.class);
                    assertThat(context.getBean(MessageBroker.class).name()).isEqualTo("rocketmq");
                });
    }

    @Test
    void ignoresRocketMqWhenAnotherBrokerIsSelected() {
        runner.withPropertyValues("pudans.messaging.broker=in-memory")
                .run(context -> assertThat(context).doesNotHaveBean(MessageBroker.class));
    }
}