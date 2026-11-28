package cn.tolim.pudans.communal.mq.broker;

import cn.tolim.pudans.communal.mq.consumer.MessageSubscription;
import cn.tolim.pudans.communal.mq.deadletter.InMemoryDeadLetterStore;
import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.retry.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMessageBrokerTests {

    private final InMemoryDeadLetterStore deadLetterStore = new InMemoryDeadLetterStore(10);

    private InMemoryMessageBroker broker(RetryPolicy retryPolicy) {
        return new InMemoryMessageBroker("test-broker", retryPolicy, deadLetterStore);
    }

    @Test
    void deliversToMatchingSubscribersOnly() {
        InMemoryMessageBroker broker = broker(RetryPolicy.noDelay(1));
        AtomicInteger orderCount = new AtomicInteger();
        AtomicInteger paymentCount = new AtomicInteger();
        broker.subscribe(MessageSubscription.of("order", message -> orderCount.incrementAndGet()));
        broker.subscribe(MessageSubscription.of("payment", message -> paymentCount.incrementAndGet()));

        broker.send(Message.of("order", "{\"id\":1}"));

        assertThat(orderCount).hasValue(1);
        assertThat(paymentCount).hasValue(0);
    }

    @Test
    void retriesAndMovesFailedMessagesToDeadLetterStore() {
        InMemoryMessageBroker broker = broker(RetryPolicy.noDelay(3));
        AtomicInteger attempts = new AtomicInteger();
        broker.subscribe(MessageSubscription.of("order", message -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("downstream unavailable");
        }));

        broker.send(Message.of("order", "x"));

        assertThat(attempts).hasValue(3);
        assertThat(deadLetterStore.size()).isEqualTo(1);
        assertThat(deadLetterStore.findAll().getFirst().attempts()).isEqualTo(3);
        assertThat(deadLetterStore.findAll().getFirst().errorType()).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void stopsRetryingOnceListenerSucceeds() {
        InMemoryMessageBroker broker = broker(RetryPolicy.noDelay(3));
        AtomicInteger attempts = new AtomicInteger();
        broker.subscribe(MessageSubscription.of("order", message -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
        }));

        broker.send(Message.of("order", "x"));

        assertThat(attempts).hasValue(2);
        assertThat(deadLetterStore.size()).isZero();
    }

    @Test
    void subscriptionHandleRemovesListener() {
        InMemoryMessageBroker broker = broker(RetryPolicy.noDelay(1));
        AtomicInteger count = new AtomicInteger();
        var handle = broker.subscribe(MessageSubscription.of("order", message -> count.incrementAndGet()));

        broker.send(Message.of("order", "x"));
        handle.close();
        broker.send(Message.of("order", "x"));

        assertThat(count).hasValue(1);
        assertThat(broker.subscriptionCount()).isZero();
    }

    @Test
    void usesMessageKeyAsMessageIdWhenPresent() {
        InMemoryMessageBroker broker = broker(RetryPolicy.noDelay(1));

        var result = broker.send(Message.of("order", "x").withKey("order-42"));

        assertThat(result.messageId()).isEqualTo("order-42");
    }

    @Test
    void deadLetterStoreEvictsOldestEntries() {
        InMemoryDeadLetterStore store = new InMemoryDeadLetterStore(2);
        InMemoryMessageBroker broker = new InMemoryMessageBroker("test-broker", RetryPolicy.noDelay(1), store);
        broker.subscribe(MessageSubscription.of("order", message -> {
            throw new IllegalStateException("failed");
        }));

        broker.send(Message.of("order", "1"));
        broker.send(Message.of("order", "2"));
        broker.send(Message.of("order", "3"));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.findAll()).extracting(record -> record.message().payload())
                .containsExactly("2", "3");
    }
}