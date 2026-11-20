package cn.tolim.pudans.core.ai.runtime.sandbox;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record SandboxCreateSpec(
        String agentName,
        @Nullable String image,
        @Nullable String entrypoint,
        double cpu,
        int memoryMb,
        int timeoutSeconds,
        boolean networkEnabled,
        String workdir,
        Map<String, String> environment) {

    public SandboxCreateSpec {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName 不能为空");
        }
        if (workdir == null || workdir.isBlank()) {
            throw new IllegalArgumentException("workdir 不能为空");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds 必须大于 0");
        }
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }
}