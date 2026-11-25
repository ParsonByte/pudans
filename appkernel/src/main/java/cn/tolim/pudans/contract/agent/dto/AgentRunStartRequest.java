package cn.tolim.pudans.contract.agent.dto;

import java.util.List;
import java.util.UUID;

public record AgentRunStartRequest(
        UUID sessionId,
        String content,
        UUID modelId,
        String answerMode,
        String reasoning,
        List<String> toolIds) {}
