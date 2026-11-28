package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;

import java.time.LocalDateTime;

// Notification template representation returned by the API.
public record TemplateResponse(String id, String code, String name, NotificationChannel channel,
                               String subject, String content, String providerTemplateCode,
                               String language, Boolean enabled, LocalDateTime createTime,
                               LocalDateTime updateTime) {

    public static TemplateResponse from(NotificationTemplateEntity entity) {
        return new TemplateResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getChannel(), entity.getSubject(), entity.getContent(),
                entity.getProviderTemplateCode(), entity.getLanguage(), entity.getEnabled(),
                entity.getCreateTime(), entity.getUpdateTime());
    }
}