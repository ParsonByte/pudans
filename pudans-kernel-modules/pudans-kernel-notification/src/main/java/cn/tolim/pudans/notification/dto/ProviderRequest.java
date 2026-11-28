package cn.tolim.pudans.notification.dto;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

// Create or update a provider configuration.
public record ProviderRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull NotificationChannel channel,
        @NotNull ProviderType provider,
        Integer priority,
        Boolean enabled,
        Map<String, String> configuration) {
}