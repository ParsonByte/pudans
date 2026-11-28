package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

// Request to deliver one notification.
public record SendNotificationRequest(
        @NotBlank @Size(max = 500) String recipient,
        NotificationChannel channel,
        @Size(max = 100) String providerCode,
        @Size(max = 100) String templateCode,
        @Size(max = 100) String providerTemplateCode,
        String subject,
        String content,
        Map<String, String> parameters,
        List<String> templateParameters,
        @Size(max = 100) String requestId,
        Boolean async,
        Integer maxAttempts) {
}