package cn.tolim.pudans.contract.live.dto;

import java.time.Instant;

// 健康视图：补充最近开播时刻，用于区分曾经开播与从未开播的源
public record LiveSourceHealth(
        String name, String state, boolean live, String roomTitle,
        String anchorName, Instant lastResolvedAt, Instant lastStreamAt,
        Instant nextCheckAt, Instant cooldownUntil, String currentRecording,
        String lastResolveCode, String lastResolveError) {}
