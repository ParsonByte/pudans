package cn.tolim.pudans.communal.mq.consumer;

import cn.tolim.pudans.communal.mq.message.Message;

// Subscription with RocketMQ style tag filtering: '*' or a '||' separated tag list.
public record MessageSubscription(String topic, String tagFilter, String consumerGroup,
                                  MessageListener listener) {

    public static final String ALL_TAGS = "*";
    public static final String DEFAULT_CONSUMER_GROUP = "pudans-default";

    public MessageSubscription {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        tagFilter = tagFilter == null || tagFilter.isBlank() ? ALL_TAGS : tagFilter.trim();
        consumerGroup = consumerGroup == null || consumerGroup.isBlank()
                ? DEFAULT_CONSUMER_GROUP
                : consumerGroup.trim();
    }

    public static MessageSubscription of(String topic, MessageListener listener) {
        return new MessageSubscription(topic, ALL_TAGS, DEFAULT_CONSUMER_GROUP, listener);
    }

    public boolean accepts(Message message) {
        if (message == null) {
            return false;
        }
        if (!topic.equals(message.topic())) {
            return false;
        }
        if (ALL_TAGS.equals(tagFilter)) {
            return true;
        }
        String tag = message.tag();
        if (tag == null || tag.isBlank()) {
            return false;
        }
        for (String candidate : tagFilter.split("\\|\\||,")) {
            if (candidate.trim().equals(tag)) {
                return true;
            }
        }
        return false;
    }
}