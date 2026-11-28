package cn.tolim.pudans.contract.mq.dto;

import java.util.List;

// Message with its delivery attempts and dead letters
public record MessageDetailResponse(MessageResponse message,
                                    List<DeliveryResponse> deliveries,
                                    List<DeadLetterResponse> deadLetters) {
}