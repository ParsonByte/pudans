package cn.tolim.pudans.contract.live.dto;

public record LiveSourceCreateRequest(
        String roomUrl, String alias, Boolean autoRecord,
        Integer segmentSeconds, Integer mergeIntervalSeconds, String storagePolicy) {}
