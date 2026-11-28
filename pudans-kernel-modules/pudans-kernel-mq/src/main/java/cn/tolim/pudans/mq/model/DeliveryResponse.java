package cn.tolim.pudans.mq.model;

import cn.tolim.pudans.mq.enums.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryResponse(String id,
                               String messageId,
                               String consumerGroup,
                               String topic,
                               DeliveryStatus status,
                               int attempts,
                               int maxAttempts,
                               LocalDateTime nextAttemptAt,
                               LocalDateTime lastAttemptAt,
                               LocalDateTime consumedAt,
                               String lastError,
                               LocalDateTime createTime) {
}