package cn.tolim.pudans.contract.live.dto;

import java.time.Instant;

public record LiveSourceHealth(
        String name, String state, boolean live, String roomTitle,
        String anchorName, Instant lastResolvedAt, Instant lastStreamAt,
        Instant nextCheckAt, Instant cooldownUntil, String currentRecording,
        String lastResolveCode, String lastResolveError) {}
