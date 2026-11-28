package cn.tolim.pudans.mq.model;

import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record DeadLetterResponse(String id,
                                 String messageId,
                                 String deliveryId,
                                 String topic,
                                 String eventType,
                                 String producer,
                                 String consumerGroup,
                                 Object payload,
                                 Map<String, String> headers,
                                 int attempts,
                                 String lastError,
                                 DeadLetterStatus status,
                                 LocalDateTime deadLetteredAt,
                                 LocalDateTime replayedAt,
                                 LocalDateTime discardedAt,
                                 CompensationStatus compensationStatus,
                                 LocalDateTime compensatedAt,
                                 String compensationError,
                                 LocalDateTime createTime) {
}