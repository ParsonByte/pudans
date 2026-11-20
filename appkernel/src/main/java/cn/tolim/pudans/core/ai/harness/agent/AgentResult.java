package cn.tolim.pudans.core.ai.harness.agent;

import dev.langchain4j.model.chat.response.ChatResponse;

public record AgentResult(String content, ChatResponse response) {

    public AgentResult {
        content = content == null ? "" : content;
    }
}