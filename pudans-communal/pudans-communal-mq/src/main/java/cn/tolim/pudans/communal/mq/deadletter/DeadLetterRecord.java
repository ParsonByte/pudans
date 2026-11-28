package cn.tolim.pudans.communal.mq.deadletter;

import cn.tolim.pudans.communal.mq.message.Message;

import java.time.Instant;

public record DeadLetterRecord(String id, Message message, Instant failedAt,
                               int attempts, String errorType, String errorMessage) {

    public DeadLetterRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        failedAt = failedAt == null ? Instant.now() : failedAt;
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be greater than or equal to 1");
        }
    }
}