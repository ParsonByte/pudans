package cn.tolim.pudans.contract.mq.dto;

import java.util.Map;

// Publish one message to the message center
public record MessagePublishRequest(String topic,
                                    String eventType,
                                    String producer,
                                    String bizKey,
                                    Object payload,
                                    Map<String, String> headers,
                                    Integer maxAttempts) {
}