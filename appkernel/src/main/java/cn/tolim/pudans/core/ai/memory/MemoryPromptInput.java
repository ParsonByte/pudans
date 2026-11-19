package cn.tolim.pudans.core.ai.memory;

public record MemoryPromptInput(String baseSystemPrompt, ResolvedMemoryPrompt resolvedPrompt) {

    public MemoryPromptInput {
        baseSystemPrompt = baseSystemPrompt == null ? "" : baseSystemPrompt;
    }

    public static MemoryPromptInput none() {
        return new MemoryPromptInput("", null);
    }
}
