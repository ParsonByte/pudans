package cn.tolim.pudans.contract.mq.dto;

import cn.tolim.pudans.contract.mq.enums.DeliveryStatus;

import java.time.LocalDateTime;

// One delivery attempt for a message and consumer group
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