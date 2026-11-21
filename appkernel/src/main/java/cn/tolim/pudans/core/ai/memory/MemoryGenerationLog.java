package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import java.util.Objects;

public record MemoryGenerationLog(
        AiMemoryPrompt.Layer memoryLevel,
        Long memoryId,
        String teamId,
        String agentId,
        String userId,
        String sessionId,
        String promptId,
        Integer promptVersion,
        MemoryPromptSource promptSource,
        String promptHash,
        String modelCode,
        String inputRef,
        String outputRef) {

    public MemoryGenerationLog {
        Objects.requireNonNull(memoryLevel, "memoryLevel");
        if (memoryId == null || memoryId <= 0) {
            throw new IllegalArgumentException("memoryId 必须大于 0");
        }
        Objects.requireNonNull(promptSource, "promptSource");
        if (promptHash == null || promptHash.isBlank()) {
            throw new IllegalArgumentException("promptHash 不能为空");
        }
    }
}
