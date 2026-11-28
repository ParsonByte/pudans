package cn.tolim.pudans.contract.mq.dto;

import cn.tolim.pudans.contract.mq.enums.CompensationStatus;
import cn.tolim.pudans.contract.mq.enums.DeadLetterStatus;

import java.time.LocalDateTime;
import java.util.Map;

// One dead letter as returned by the message center API
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