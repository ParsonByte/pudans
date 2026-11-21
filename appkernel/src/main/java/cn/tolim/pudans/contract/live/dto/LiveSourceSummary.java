package cn.tolim.pudans.contract.live.dto;

public record LiveSourceSummary(
        long total, long enabled, long live, long recording,
        long offline, long error, long rateLimited) {}
