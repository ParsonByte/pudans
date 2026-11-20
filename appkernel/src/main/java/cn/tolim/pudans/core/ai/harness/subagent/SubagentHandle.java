package cn.tolim.pudans.core.ai.harness.subagent;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record SubagentHandle(
        String agentKey,
        String agentId,
        String sessionId,
        @Nullable String label,
        boolean reusable,
        boolean busy) {

    public SubagentHandle {
        agentKey = Objects.requireNonNull(agentKey, "子代理键不能为空");
        agentId = Objects.requireNonNull(agentId, "子代理标识不能为空");
        sessionId = Objects.requireNonNull(sessionId, "子会话标识不能为空");
    }
}