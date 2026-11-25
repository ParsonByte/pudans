package cn.tolim.pudans.contract.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MemoryUpdateRequest(
        String memoryType, String summary, BigDecimal confidence, Instant validTo) {}
