package cn.tolim.pudans.contract.mq.dto;

import cn.tolim.pudans.contract.mq.enums.MessageStatus;

import java.time.LocalDateTime;
import java.util.Map;

// One message as returned by the message center API
public record MessageResponse(String id,
                              String bizKey,
                              String topic,
                              String eventType,
                              String producer,
                              Object payload,
                              Map<String, String> headers,
                              MessageStatus status,
                              int attempts,
                              int maxAttempts,
                              LocalDateTime nextAttemptAt,
                              LocalDateTime lastAttemptAt,
                              LocalDateTime deliveredAt,
                              LocalDateTime consumedAt,
                              LocalDateTime deadLetterAt,
                              String lastError,
                              LocalDateTime createTime,
                              LocalDateTime updateTime) {
}