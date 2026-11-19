package cn.tolim.pudans.core.ai.gateway;

public record AiStreamEvent(
        String content,
        String reasoning,
        ToolCallDelta toolCall,
        AiTokenUsage usage,
        String finishReason) {

    public record ToolCallDelta(Integer index, String id, String name, String arguments) {}
}
