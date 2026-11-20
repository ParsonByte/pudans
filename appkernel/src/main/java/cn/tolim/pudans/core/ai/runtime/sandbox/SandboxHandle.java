package cn.tolim.pudans.core.ai.runtime.sandbox;

import org.jspecify.annotations.Nullable;

public record SandboxHandle(String sandboxId, @Nullable String endpoint) {

    public SandboxHandle {
        if (sandboxId == null || sandboxId.isBlank()) {
            throw new IllegalArgumentException("sandboxId 不能为空");
        }
    }
}