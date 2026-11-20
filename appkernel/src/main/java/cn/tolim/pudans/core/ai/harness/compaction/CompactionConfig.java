package cn.tolim.pudans.core.ai.harness.compaction;

public record CompactionConfig(int triggerMessages, int keepMessages) {

    public CompactionConfig {
        triggerMessages = Math.max(0, triggerMessages);
        keepMessages = Math.max(1, keepMessages);
    }

    public static CompactionConfig disabled() {
        return new CompactionConfig(0, 1);
    }

    public boolean enabled() {
        return triggerMessages > 0;
    }
}