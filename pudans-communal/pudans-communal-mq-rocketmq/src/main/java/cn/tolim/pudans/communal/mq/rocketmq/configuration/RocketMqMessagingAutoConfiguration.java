package cn.tolim.pudans.communal.mq.rocketmq.configuration;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import cn.tolim.pudans.communal.mq.configuration.PudansMessagingAutoConfiguration;
import cn.tolim.pudans.communal.mq.rocketmq.broker.RocketMqMessageBroker;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = PudansMessagingAutoConfiguration.class)
@ConditionalOnClass({DefaultMQProducer.class, MessageBroker.class})
@ConditionalOnProperty(prefix = "pudans.messaging", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(RocketMqMessagingProperties.class)
public class RocketMqMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageBroker.class)
    @ConditionalOnProperty(prefix = "pudans.messaging", name = "broker", havingValue = "rocketmq")
    public MessageBroker rocketMqMessageBroker(RocketMqMessagingProperties properties) {
        return new RocketMqMessageBroker(properties);
    }
}