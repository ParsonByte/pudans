package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import java.util.Objects;

public record ResolvedMemoryPrompt(
        AiMemoryPrompt.Layer layer,
        MemoryPromptSource source,
        String promptId,
        int version,
        String prompt) {

    public ResolvedMemoryPrompt {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(source, "source");
        if (version < 0) {
            throw new IllegalArgumentException("version 不能为负数");
        }
        if (source != MemoryPromptSource.SYSTEM) {
            if (promptId == null || promptId.isBlank()) {
                throw new IllegalArgumentException("promptId 不能为空");
            }
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("prompt 不能为空");
            }
        }
    }

    public static ResolvedMemoryPrompt system(AiMemoryPrompt.Layer layer) {
        return new ResolvedMemoryPrompt(layer, MemoryPromptSource.SYSTEM, null, 0, null);
    }

    public boolean isCustomStrategy() {
        return source != MemoryPromptSource.SYSTEM && prompt != null && !prompt.isBlank();
    }
}
