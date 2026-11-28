package cn.tolim.pudans.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

// Ad hoc message used to verify a provider configuration.
public record ProviderTestRequest(
        @NotBlank String recipient,
        String subject,
        String content,
        String providerTemplateCode,
        Map<String, String> parameters,
        List<String> templateParameters) {
}