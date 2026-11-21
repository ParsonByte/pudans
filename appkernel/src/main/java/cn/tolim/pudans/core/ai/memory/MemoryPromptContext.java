package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import java.util.Objects;

public record MemoryPromptContext(
        AiMemoryPrompt.Layer layer,
        String systemPrompt,
        String promptId,
        Integer promptVersion,
        MemoryPromptSource promptSource,
        String promptHash) {

    public MemoryPromptContext {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(promptSource, "promptSource");
        Objects.requireNonNull(promptHash, "promptHash");
    }

    public static MemoryPromptContext compose(
            MemoryPromptResolver resolver, AiMemoryPrompt.Layer layer, MemoryPromptInput input) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(layer, "layer");
        var promptInput = input == null ? MemoryPromptInput.none() : input;
        var resolved = promptInput.resolvedPrompt();
        if (resolved != null && resolved.layer() != layer) {
            throw new IllegalArgumentException("Prompt 层级与流水线层级不一致");
        }
        var systemPrompt = resolver.appendStrategy(promptInput.baseSystemPrompt(), resolved);
        return new MemoryPromptContext(
                layer,
                systemPrompt,
                resolved == null ? null : resolved.promptId(),
                resolved == null ? null : resolved.version(),
                resolved == null ? MemoryPromptSource.SYSTEM : resolved.source(),
                MemoryHashes.sha256Hex(systemPrompt));
    }

    public MemoryGenerationLog toGenerationLog(
            long memoryId,
            String teamId,
            String agentId,
            String userId,
            String sessionId,
            MemoryGenerationInput generation) {
        var generationInput = generation == null ? MemoryGenerationInput.empty() : generation;
        return new MemoryGenerationLog(
                layer,
                memoryId,
                teamId,
                agentId,
                userId,
                sessionId,
                promptId,
                promptVersion,
                promptSource,
                promptHash,
                generationInput.modelCode(),
                generationInput.inputRef(),
                generationInput.outputRef());
    }
}
