package cn.tolim.pudans.mq.config;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import cn.tolim.pudans.mq.dispatch.RocketMqAnnouncementSubscriber;
import cn.tolim.pudans.mq.producer.LocalMessageTransport;
import cn.tolim.pudans.mq.producer.MessageTransport;
import cn.tolim.pudans.mq.producer.RocketMqMessageTransport;
import cn.tolim.pudans.mq.service.MessageDeliveryService;
import cn.tolim.pudans.mq.service.RetryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MessageCenterProperties.class)
public class MessageCenterConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock messageClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnMissingBean(RetryPolicy.class)
    public RetryPolicy messageRetryPolicy(MessageCenterProperties properties) {
        return new RetryPolicy(properties.getRetryInitialDelayMs(), properties.getRetryMaxDelayMs(),
                properties.getRetryMultiplier());
    }

    // Local mode keeps the in-process event path and the scheduler as the safety net
    @Bean
    @ConditionalOnMissingBean(MessageTransport.class)
    @ConditionalOnProperty(prefix = "pudans.messaging", name = "broker", havingValue = "in-memory",
            matchIfMissing = true)
    public MessageTransport localMessageTransport(ApplicationEventPublisher publisher) {
        return new LocalMessageTransport(publisher);
    }

    @Bean
    @ConditionalOnMissingBean(MessageTransport.class)
    @ConditionalOnProperty(prefix = "pudans.messaging", name = "broker", havingValue = "rocketmq")
    public MessageTransport rocketMqMessageTransport(MessageBroker messageBroker,
                                                     MessageCenterProperties properties,
                                                     ObjectMapper objectMapper) {
        return new RocketMqMessageTransport(messageBroker, properties.getAnnouncementTopic(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "pudans.messaging", name = "broker", havingValue = "rocketmq")
    public RocketMqAnnouncementSubscriber rocketMqAnnouncementSubscriber(
            MessageBroker messageBroker,
            MessageDeliveryService deliveryService,
            MessageCenterProperties properties,
            ObjectMapper objectMapper) {
        return new RocketMqAnnouncementSubscriber(messageBroker, deliveryService, objectMapper,
                properties.getAnnouncementTopic(), properties.getAnnouncementConsumerGroup());
    }
}