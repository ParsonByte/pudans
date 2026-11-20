package cn.tolim.pudans.core.ai.memory.asset;

import java.time.Instant;

public record MemoryL2Detail(Long id, String path, String title, String content, Instant updatedAt) {}