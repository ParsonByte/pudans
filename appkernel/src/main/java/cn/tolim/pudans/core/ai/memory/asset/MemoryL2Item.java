package cn.tolim.pudans.core.ai.memory.asset;

import java.time.Instant;

public record MemoryL2Item(Long id, String path, String summary, Instant updatedAt) {}