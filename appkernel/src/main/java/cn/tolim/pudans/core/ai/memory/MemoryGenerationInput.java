package cn.tolim.pudans.core.ai.memory;

public record MemoryGenerationInput(String modelCode, String inputRef, String outputRef) {

    public static MemoryGenerationInput empty() {
        return new MemoryGenerationInput(null, null, null);
    }
}
