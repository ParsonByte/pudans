package cn.tolim.pudans.communal.mq.health;

import cn.tolim.pudans.communal.mq.broker.MessageBroker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.SmartLifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class MessageBrokerHealthIndicatorTests {

    @Test
    void reportsUpForBrokerWithoutLifecycle() {
        MessageBroker broker = mock(MessageBroker.class);
        when(broker.name()).thenReturn("in-memory");
        when(broker.subscriptionCount()).thenReturn(0);
        Health health = new MessageBrokerHealthIndicator(broker).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenLifecycleIsStopped() {
        MessageBroker broker = mock(MessageBroker.class,
                withSettings().extraInterfaces(SmartLifecycle.class));
        when(broker.name()).thenReturn("rocketmq");
        when(((SmartLifecycle) broker).isRunning()).thenReturn(false);
        Health health = new MessageBrokerHealthIndicator(broker).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("broker", "rocketmq");
    }

    @Test
    void reportsUpWhenLifecycleIsRunning() {
        MessageBroker broker = mock(MessageBroker.class,
                withSettings().extraInterfaces(SmartLifecycle.class));
        when(broker.name()).thenReturn("rocketmq");
        when(broker.subscriptionCount()).thenReturn(1);
        when(((SmartLifecycle) broker).isRunning()).thenReturn(true);
        Health health = new MessageBrokerHealthIndicator(broker).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }
}