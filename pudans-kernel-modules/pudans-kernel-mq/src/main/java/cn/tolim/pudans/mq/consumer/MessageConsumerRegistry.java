package cn.tolim.pudans.mq.consumer;

import cn.tolim.pudans.mq.model.ConsumerDescriptor;
import cn.tolim.pudans.mq.util.MessageTexts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageConsumerRegistry {

    public static final String WILDCARD_TOPIC = "*";

    private final Map<String, MessageConsumer> consumers = new ConcurrentHashMap<>();

    public MessageConsumerRegistry(ObjectProvider<MessageConsumer> consumerProvider) {
        consumerProvider.orderedStream().forEach(this::register);
    }

    public void register(MessageConsumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is required");
        }
        String consumerGroup = MessageTexts.trimToNull(consumer.consumerGroup());
        if (consumerGroup == null) {
            throw new IllegalArgumentException("consumer group is required");
        }
        Set<String> topics = consumer.topics();
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("consumer " + consumerGroup
                    + " must declare at least one topic");
        }
        MessageConsumer previous = consumers.putIfAbsent(consumerGroup, consumer);
        if (previous != null && previous != consumer) {
            throw new IllegalStateException("Duplicate consumer group: " + consumerGroup);
        }
    }

    public void unregister(String consumerGroup) {
        consumers.remove(consumerGroup);
    }

    public List<MessageConsumer> consumersFor(String topic) {
        if (MessageTexts.isBlank(topic)) {
            return List.of();
        }
        return consumers.values().stream()
                .filter(consumer -> consumer.topics().contains(WILDCARD_TOPIC)
                        || consumer.topics().contains(topic))
                .sorted(Comparator.comparing(MessageConsumer::consumerGroup))
                .toList();
    }

    public List<ConsumerDescriptor> descriptors() {
        return consumers.values().stream()
                .map(consumer -> new ConsumerDescriptor(consumer.consumerGroup(),
                        Set.copyOf(consumer.topics())))
                .sorted(Comparator.comparing(ConsumerDescriptor::consumerGroup))
                .toList();
    }

    public int size() {
        return consumers.size();
    }
}