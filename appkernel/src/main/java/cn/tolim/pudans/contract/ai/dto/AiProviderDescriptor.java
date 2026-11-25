package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiProviderDescriptor(
        String provider,
        String displayName,
        String description,
        String websiteUrl,
        String documentationUrl,
        String defaultBaseUrl,
        String defaultProtocol,
        List<String> protocols,
        List<String> modelTypes,
        List<String> catalogProviders,
        boolean requiresBaseUrl,
        List<AiProviderCapability> capabilities) {

    public AiProviderDescriptor {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
