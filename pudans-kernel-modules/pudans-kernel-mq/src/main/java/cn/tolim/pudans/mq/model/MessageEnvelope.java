package cn.tolim.pudans.mq.model;

import java.time.LocalDateTime;
import java.util.Map;

public record MessageEnvelope(String id,
                              String bizKey,
                              String topic,
                              String eventType,
                              String producer,
                              String payload,
                              Map<String, String> headers,
                              int attempts,
                              LocalDateTime createdAt) {
}