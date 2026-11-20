package cn.tolim.pudans.core.ai.runtime.sandbox;

import org.jspecify.annotations.Nullable;

public record SandboxStatus(SandboxState state, @Nullable String message) {

    public SandboxStatus {
        if (state == null) {
            throw new IllegalArgumentException("state 不能为空");
        }
    }
}