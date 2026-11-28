package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Create or replace a notification template.
public record TemplateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull NotificationChannel channel,
        String subject,
        String content,
        @Size(max = 100) String providerTemplateCode,
        @Size(max = 20) String language,
        Boolean enabled) {
}