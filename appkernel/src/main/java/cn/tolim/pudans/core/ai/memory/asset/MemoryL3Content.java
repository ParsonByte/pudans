package cn.tolim.pudans.core.ai.memory.asset;

import java.time.Instant;

public record MemoryL3Content(Long id, String content, Instant updatedAt) {}