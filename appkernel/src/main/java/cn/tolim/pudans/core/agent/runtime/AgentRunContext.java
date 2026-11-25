package cn.tolim.pudans.core.agent.runtime;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AgentRunContext(
        UUID runId,
        UUID sessionId,
        String ownerId,
        String workspaceId,
        UUID modelId,
        String answerMode,
        String reasoning,
        List<String> toolIds,
        Set<String> roles,
        String prompt) {

    public AgentRunContext {
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
