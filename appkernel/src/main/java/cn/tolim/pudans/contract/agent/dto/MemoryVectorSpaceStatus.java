package cn.tolim.pudans.contract.agent.dto;

import java.time.Instant;

public record MemoryVectorSpaceStatus(
        String modelKey,
        String tableName,
        String status,
        boolean ready,
        String reasonCode,
        Instant checkedAt) {}
