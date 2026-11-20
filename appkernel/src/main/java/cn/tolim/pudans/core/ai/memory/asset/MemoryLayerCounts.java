package cn.tolim.pudans.core.ai.memory.asset;

public record MemoryLayerCounts(long L0, long L1, long L2, long L3) {

    public static MemoryLayerCounts zero() {
        return new MemoryLayerCounts(0, 0, 0, 0);
    }
}