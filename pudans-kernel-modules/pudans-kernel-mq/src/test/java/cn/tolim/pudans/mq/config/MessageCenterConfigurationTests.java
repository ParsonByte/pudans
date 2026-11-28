package cn.tolim.pudans.mq.config;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import cn.tolim.pudans.mq.dispatch.RocketMqAnnouncementSubscriber;
import cn.tolim.pudans.mq.producer.LocalMessageTransport;
import cn.tolim.pudans.mq.producer.MessageTransport;
import cn.tolim.pudans.mq.producer.RocketMqMessageTransport;
import cn.tolim.pudans.mq.service.MessageDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessageCenterConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MessageCenterConfiguration.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(MessageBroker.class, () -> mock(MessageBroker.class))
            .withBean(MessageDeliveryService.class, () -> mock(MessageDeliveryService.class));

    @Test
    void usesLocalTransportByDefault() {
        runner.run(context -> assertThat(context.getBean(MessageTransport.class))
                .isInstanceOf(LocalMessageTransport.class));
    }

    @Test
    void usesRocketMqTransportAndSubscriberWhenSelected() {
        runner.withPropertyValues("pudans.messaging.broker=rocketmq")
                .run(context -> {
                    assertThat(context.getBean(MessageTransport.class))
                            .isInstanceOf(RocketMqMessageTransport.class);
                    assertThat(context).hasSingleBean(RocketMqAnnouncementSubscriber.class);
                });
    }
}