package cn.tolim.pudans.mq.model;

import java.util.List;

public record MessageDetailResponse(MessageResponse message,
                                    List<DeliveryResponse> deliveries,
                                    List<DeadLetterResponse> deadLetters) {
}