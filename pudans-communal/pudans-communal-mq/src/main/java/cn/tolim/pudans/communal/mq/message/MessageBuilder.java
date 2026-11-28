package cn.tolim.pudans.communal.mq.message;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageBuilder {

    private final String topic;
    private final String payload;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String tag;
    private String key;
    private Instant createdAt;

    private MessageBuilder(String topic, String payload) {
        this.topic = topic;
        this.payload = payload;
    }

    public static MessageBuilder create(String topic, String payload) {
        return new MessageBuilder(topic, payload);
    }

    public MessageBuilder tag(String tag) {
        this.tag = tag;
        return this;
    }

    public MessageBuilder key(String key) {
        this.key = key;
        return this;
    }

    public MessageBuilder header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public MessageBuilder headers(Map<String, String> values) {
        if (values != null) {
            this.headers.putAll(values);
        }
        return this;
    }

    public MessageBuilder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Message build() {
        return new Message(topic, tag, key, payload, headers, createdAt);
    }
}