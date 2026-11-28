package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.enums.SendStatus;

import java.util.Map;

// Aggregate send record counts.
public record NotificationStatsResponse(Map<SendStatus, Long> sendStatusCounts,
                                        long pendingDeadLetters) {
}