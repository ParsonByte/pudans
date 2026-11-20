package cn.tolim.pudans.core.ai.harness.tool;

public record ToolResultEvictionConfig(int maxChars) {

    public ToolResultEvictionConfig {
        maxChars = Math.max(0, maxChars);
    }

    public static ToolResultEvictionConfig disabled() {
        return new ToolResultEvictionConfig(0);
    }

    public boolean enabled() {
        return maxChars > 0;
    }
}