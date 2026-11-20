package cn.tolim.pudans.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.core.webhook.WebhookEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DomainEventPublisherTest {

    // 队列容量固定为 8192，多投递一条即可触发一次溢出
    private static final int QUEUE_CAPACITY = 8192;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final DomainEventPublisher publisher = new DomainEventPublisher(
            mock(ApplicationEventPublisher.class), mock(WebhookEventPublisher.class), meterRegistry);

    @Test
    void incrementsDroppedCounterWhenTheQueueOverflows() {
        for (var index = 0; index < QUEUE_CAPACITY + 1; index++) {
            publisher.publish("test-event", Map.of("index", index), "trace");
        }

        var counter = meterRegistry.get("pudans.domain.events.dropped").tag("type", "test-event").counter();
        assertThat(counter.count()).isEqualTo(1.0d);
    }

    @Test
    void keepsNoDroppedCounterWhenTheQueueHasRoom() {
        publisher.publish("test-event", Map.of("index", 1), "trace");

        assertThat(meterRegistry.find("pudans.domain.events.dropped").counter()).isNull();
    }
}
