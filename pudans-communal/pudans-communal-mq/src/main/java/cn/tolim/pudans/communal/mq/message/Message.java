package cn.tolim.pudans.communal.mq.message;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Provider-neutral message envelope; RocketMQ topic/tag/key semantics are preserved.
public record Message(String topic, String tag, String key, String payload,
                      Map<String, String> headers, Instant createdAt) {

    public Message {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static Message of(String topic, String payload) {
        return new Message(topic, null, null, payload, Map.of(), Instant.now());
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name));
    }

    public Message withTag(String newTag) {
        return new Message(topic, newTag, key, payload, headers, createdAt);
    }

    public Message withKey(String newKey) {
        return new Message(topic, tag, newKey, payload, headers, createdAt);
    }

    public Message withHeader(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("header name must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("header value must not be null");
        }
        Map<String, String> updatedHeaders = new LinkedHashMap<>(headers);
        updatedHeaders.put(name, value);
        return new Message(topic, tag, key, payload, updatedHeaders, createdAt);
    }
}