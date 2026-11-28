package cn.tolim.pudans.communal.mq.message;

import java.time.Instant;

public record SendResult(String messageId, String topic, Instant acceptedAt) {

    public SendResult {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        acceptedAt = acceptedAt == null ? Instant.now() : acceptedAt;
    }
}