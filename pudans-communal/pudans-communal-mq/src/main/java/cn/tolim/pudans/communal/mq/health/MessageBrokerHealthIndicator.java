package cn.tolim.pudans.communal.mq.health;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.SmartLifecycle;

public class MessageBrokerHealthIndicator implements HealthIndicator {

    private final MessageBroker messageBroker;

    public MessageBrokerHealthIndicator(MessageBroker messageBroker) {
        if (messageBroker == null) {
            throw new IllegalArgumentException("messageBroker must not be null");
        }
        this.messageBroker = messageBroker;
    }

    @Override
    public Health health() {
        if (messageBroker instanceof SmartLifecycle lifecycle && !lifecycle.isRunning()) {
            return Health.down()
                    .withDetail("broker", messageBroker.name())
                    .build();
        }
        return Health.up()
                .withDetail("broker", messageBroker.name())
                .withDetail("subscriptions", messageBroker.subscriptionCount())
                .build();
    }
}