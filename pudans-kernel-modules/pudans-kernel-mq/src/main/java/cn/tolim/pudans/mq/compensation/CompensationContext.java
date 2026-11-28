package cn.tolim.pudans.mq.compensation;

import java.util.Map;

public record CompensationContext(String deadLetterId,
                                  String messageId,
                                  String topic,
                                  String eventType,
                                  String payload,
                                  Map<String, String> headers,
                                  int attempts,
                                  String lastError) {
}