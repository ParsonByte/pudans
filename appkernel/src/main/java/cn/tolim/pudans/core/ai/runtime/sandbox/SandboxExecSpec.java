package cn.tolim.pudans.core.ai.runtime.sandbox;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record SandboxExecSpec(
        String command,
        List<String> args,
        @Nullable String workdir,
        Map<String, String> environment,
        int timeoutSeconds) {

    public SandboxExecSpec {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }
        args = args == null ? List.of() : List.copyOf(args);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds 必须大于 0");
        }
    }
}