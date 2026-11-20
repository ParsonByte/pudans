package cn.tolim.pudans.core.ai.runtime.sandbox;

import cn.tolim.pudans.contract.core.extension.ai.AiSandboxProvider;
import org.jspecify.annotations.Nullable;

public record SandboxProviderConfig(
        AiSandboxProvider.Provider provider,
        String displayName,
        @Nullable String endpoint,
        @Nullable String region,
        @Nullable String accessKeyId,
        String accessKeySecret,
        @Nullable String defaultImage,
        int maxTimeoutSeconds) {

    public SandboxProviderConfig {
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        if (accessKeySecret == null || accessKeySecret.isBlank()) {
            throw new IllegalArgumentException("accessKeySecret 不能为空");
        }
        if (maxTimeoutSeconds <= 0) {
            maxTimeoutSeconds = 3600;
        }
    }
}