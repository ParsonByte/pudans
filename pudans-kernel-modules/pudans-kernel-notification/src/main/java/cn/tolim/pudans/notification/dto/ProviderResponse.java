package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;

import java.time.LocalDateTime;
import java.util.Map;

// Provider representation with secrets masked.
public record ProviderResponse(String id, String code, String name, NotificationChannel channel,
                               ProviderType provider, Integer priority, Boolean enabled,
                               Map<String, String> configuration, LocalDateTime createTime,
                               LocalDateTime updateTime) {

    public static ProviderResponse from(NotificationProviderEntity entity,
                                        Map<String, String> maskedConfiguration) {
        return new ProviderResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getChannel(), entity.getProvider(), entity.getPriority(),
                entity.getEnabled(), maskedConfiguration, entity.getCreateTime(),
                entity.getUpdateTime());
    }
}