package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.enums.SendStatus;

import java.time.LocalDateTime;

// State of a notification send record after a send or retry attempt.
public record SendNotificationResponse(String id, String requestId, String templateCode,
                                       NotificationChannel channel, String providerCode,
                                       ProviderType providerType, String recipient,
                                       String subject, SendStatus status, Integer attempts,
                                       Integer maxAttempts, LocalDateTime nextRetryAt,
                                       String providerMessageId, String lastError,
                                       LocalDateTime sentAt, LocalDateTime createTime) {

    public static SendNotificationResponse from(NotificationSendRecordEntity entity) {
        return new SendNotificationResponse(entity.getId(), entity.getRequestId(),
                entity.getTemplateCode(), entity.getChannel(), entity.getProviderCode(),
                entity.getProviderType(), entity.getRecipient(), entity.getSubject(),
                entity.getStatus(), entity.getAttempts(), entity.getMaxAttempts(),
                entity.getNextRetryAt(), entity.getProviderMessageId(), entity.getLastError(),
                entity.getSentAt(), entity.getCreateTime());
    }
}