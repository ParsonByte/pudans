package cn.tolim.pudans.contract.agent.dto;

import java.time.Instant;
import java.util.List;

public record UnderstandingMap(
        List<UnderstandingMapEntry> dimensions, int totalMemories, Instant generatedAt) {}
