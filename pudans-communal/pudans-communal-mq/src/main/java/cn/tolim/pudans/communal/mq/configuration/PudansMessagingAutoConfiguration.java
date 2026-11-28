package cn.tolim.pudans.communal.mq.configuration;

import cn.tolim.pudans.communal.mq.broker.InMemoryMessageBroker;
import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import cn.tolim.pudans.communal.mq.deadletter.DeadLetterStore;
import cn.tolim.pudans.communal.mq.deadletter.InMemoryDeadLetterStore;
import cn.tolim.pudans.communal.mq.health.MessageBrokerHealthIndicator;
import cn.tolim.pudans.communal.mq.producer.MessageProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "pudans.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PudansMessagingProperties.class)
public class PudansMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pudans.messaging", name = "broker", havingValue = "in-memory",
            matchIfMissing = true)
    public DeadLetterStore pudansDeadLetterStore(PudansMessagingProperties properties) {
        return new InMemoryDeadLetterStore(properties.getDeadLetterCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pudans.messaging", name = "broker", havingValue = "in-memory",
            matchIfMissing = true)
    public MessageBroker pudansInMemoryMessageBroker(PudansMessagingProperties properties,
                                                     DeadLetterStore deadLetterStore) {
        return new InMemoryMessageBroker(properties.getBrokerName(), properties.toRetryPolicy(), deadLetterStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageProducer pudansMessageProducer(MessageBroker messageBroker) {
        return messageBroker;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageBrokerHealthIndicator pudansMessageBrokerHealthIndicator(MessageBroker messageBroker) {
        return new MessageBrokerHealthIndicator(messageBroker);
    }
}