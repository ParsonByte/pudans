package cn.tolim.pudans.core.ai.routing.classifier;

public record ComplexityInput(String lastUserMessage, int promptTokens) {

    public ComplexityInput {
        if (promptTokens < 0) {
            throw new IllegalArgumentException("promptTokens 不能为负数");
        }
    }
}
