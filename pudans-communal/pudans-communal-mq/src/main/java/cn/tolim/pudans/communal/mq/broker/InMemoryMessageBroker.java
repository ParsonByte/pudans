package cn.tolim.pudans.communal.mq.broker;

import cn.tolim.pudans.communal.mq.consumer.MessageSubscription;
import cn.tolim.pudans.communal.mq.deadletter.DeadLetterRecord;
import cn.tolim.pudans.communal.mq.deadletter.DeadLetterStore;
import cn.tolim.pudans.communal.mq.message.Message;
import cn.tolim.pudans.communal.mq.message.SendResult;
import cn.tolim.pudans.communal.mq.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Synchronous in-memory broker used for local development and tests before RocketMQ wiring.
public class InMemoryMessageBroker implements MessageBroker {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryMessageBroker.class);

    private final String name;
    private final RetryPolicy retryPolicy;
    private final DeadLetterStore deadLetterStore;
    private final Map<String, CopyOnWriteArrayList<MessageSubscription>> subscriptions = new ConcurrentHashMap<>();

    public InMemoryMessageBroker(String name, RetryPolicy retryPolicy, DeadLetterStore deadLetterStore) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retryPolicy must not be null");
        }
        if (deadLetterStore == null) {
            throw new IllegalArgumentException("deadLetterStore must not be null");
        }
        this.name = name;
        this.retryPolicy = retryPolicy;
        this.deadLetterStore = deadLetterStore;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SendResult send(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        List<MessageSubscription> targets = subscriptions.getOrDefault(message.topic(), new CopyOnWriteArrayList<>());
        for (MessageSubscription subscription : targets) {
            if (subscription.accepts(message)) {
                deliverWithRetry(message, subscription);
            }
        }
        String messageId = message.key() == null || message.key().isBlank()
                ? UUID.randomUUID().toString()
                : message.key();
        return new SendResult(messageId, message.topic(), Instant.now());
    }

    @Override
    public SubscriptionHandle subscribe(MessageSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        CopyOnWriteArrayList<MessageSubscription> topicSubscriptions =
                subscriptions.computeIfAbsent(subscription.topic(), topic -> new CopyOnWriteArrayList<>());
        topicSubscriptions.add(subscription);
        return () -> removeSubscription(subscription);
    }

    public boolean unsubscribe(MessageSubscription subscription) {
        return removeSubscription(subscription);
    }

    @Override
    public int subscriptionCount() {
        return subscriptions.values().stream().mapToInt(List::size).sum();
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public DeadLetterStore deadLetterStore() {
        return deadLetterStore;
    }

    private boolean removeSubscription(MessageSubscription subscription) {
        CopyOnWriteArrayList<MessageSubscription> topicSubscriptions = subscriptions.get(subscription.topic());
        if (topicSubscriptions == null) {
            return false;
        }
        boolean removed = topicSubscriptions.remove(subscription);
        if (topicSubscriptions.isEmpty()) {
            subscriptions.remove(subscription.topic(), topicSubscriptions);
        }
        return removed;
    }

    private void deliverWithRetry(Message message, MessageSubscription subscription) {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            try {
                subscription.listener().onMessage(message);
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                LOGGER.warn("message delivery failed: topic={}, group={}, attempt={}/{}",
                        message.topic(), subscription.consumerGroup(), attempt, retryPolicy.maxAttempts(), exception);
                if (attempt >= retryPolicy.maxAttempts()) {
                    break;
                }
                if (!pause(retryPolicy.backoffForAttempt(attempt))) {
                    break;
                }
            }
        }
        recordDeadLetter(message, subscription, lastFailure);
    }

    // Returns false when interrupted so the message goes to the dead letter store.
    private boolean pause(Duration backoff) {
        if (backoff.isZero() || backoff.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(backoff.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void recordDeadLetter(Message message, MessageSubscription subscription, Exception failure) {
        DeadLetterRecord record = new DeadLetterRecord(
                UUID.randomUUID().toString(),
                message,
                Instant.now(),
                retryPolicy.maxAttempts(),
                failure == null ? "unknown" : failure.getClass().getName(),
                failure == null ? null : failure.getMessage());
        deadLetterStore.store(record);
        LOGGER.error("message moved to dead letter store: topic={}, group={}, messageId={}",
                message.topic(), subscription.consumerGroup(), record.id());
    }
}