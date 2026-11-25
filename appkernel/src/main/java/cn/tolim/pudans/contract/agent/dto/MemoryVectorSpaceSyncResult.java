package cn.tolim.pudans.contract.agent.dto;

public record MemoryVectorSpaceSyncResult(
        int checked, int available, int disabled, String reasonCode) {}
