package cn.tolim.pudans.core.ai.memory.prompt;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.contract.ai.AiMemoryPromptBinding;
import java.time.Instant;

public record MemoryPromptLog(
        Long id,
        String promptId,
        Integer version,
        Action action,
        AiMemoryPromptBinding.TargetType targetType,
        String team,
        String agent,
        AiMemoryPrompt.Layer layer,
        String actor,
        Instant createdAt) {

    public enum Action {
        APPLY,
        CLEAR,
        UPDATE
    }
}