package cn.tolim.pudans.contract.live.dto;

// 录制汇总：状态计数与存储字节
public record LiveRecordingSummary(
        long total, long active, long completed, long failed, long canceled,
        long totalBytes, long localBytes) {}
