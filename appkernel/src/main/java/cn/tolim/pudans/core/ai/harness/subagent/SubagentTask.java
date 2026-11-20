package cn.tolim.pudans.core.ai.harness.subagent;

import cn.tolim.pudans.core.ai.harness.agent.AgentRunStatus;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record SubagentTask(
        @Nullable String taskId,
        String agentKey,
        String agentId,
        String sessionId,
        @Nullable String label,
        AgentRunStatus state,
        String result,
        String error,
        boolean background,
        Instant createdAt,
        Instant updatedAt) {

    public SubagentTask {
        agentKey = Objects.requireNonNull(agentKey, "子代理键不能为空");
        agentId = Objects.requireNonNull(agentId, "子代理标识不能为空");
        sessionId = Objects.requireNonNull(sessionId, "子会话标识不能为空");
        state = Objects.requireNonNull(state, "子代理任务状态不能为空");
        result = result == null ? "" : result;
        error = error == null ? "" : error;
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    public boolean terminal() {
        return state == AgentRunStatus.COMPLETED
                || state == AgentRunStatus.FAILED
                || state == AgentRunStatus.CANCELLED;
    }
}