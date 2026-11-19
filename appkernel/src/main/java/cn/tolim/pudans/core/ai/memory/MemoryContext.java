package cn.tolim.pudans.core.ai.memory;

public record MemoryContext(String teamId, String userId, String agentId, String sessionId, String taskId) {

    public MemoryContext {
        requireText(teamId, "teamId");
        requireText(userId, "userId");
        requireText(agentId, "agentId");
        requireText(sessionId, "sessionId");
        if (taskId != null && taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空白");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
