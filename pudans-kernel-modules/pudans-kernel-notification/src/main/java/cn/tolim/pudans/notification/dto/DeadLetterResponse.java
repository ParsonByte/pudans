package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.entity.NotificationDeadLetterEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;

import java.time.LocalDateTime;

// Dead letter representation returned by the API.
public record DeadLetterResponse(String id, String recordId, String requestId, String templateCode,
                                 NotificationChannel channel, String providerCode,
                                 ProviderType providerType, String recipient, String subject,
                                 Integer attempts, String lastError, LocalDateTime failedAt,
                                 Boolean replayed, LocalDateTime replayedAt,
                                 LocalDateTime createTime) {

    public static DeadLetterResponse from(NotificationDeadLetterEntity entity) {
        return new DeadLetterResponse(entity.getId(), entity.getRecordId(), entity.getRequestId(),
                entity.getTemplateCode(), entity.getChannel(), entity.getProviderCode(),
                entity.getProviderType(), entity.getRecipient(), entity.getSubject(),
                entity.getAttempts(), entity.getLastError(), entity.getFailedAt(),
                entity.getReplayed(), entity.getReplayedAt(), entity.getCreateTime());
    }
}