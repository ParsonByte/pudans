package cn.tolim.pudans.contract.live;

public record LiveSourceSummary(
        long total, long enabled, long live, long recording,
        long offline, long error, long rateLimited) {}
