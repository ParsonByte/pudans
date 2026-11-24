package cn.tolim.pudans.contract.live.dto;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import java.time.Instant;

// 录制视图：补充最近写入与收包时刻，用于识别已经卡住的录制
public record LiveRecordingView(
        String name, String sourceName, LivePlatform platform, String roomId,
        String roomTitle, String anchorName, String format,
        LiveRecordingState state, String errorCode, Instant startedAt,
        Instant endedAt, long durationMillis, long totalBytes,
        int segmentCount, int partCount, long localBytes,
        Instant lastWriteAt, Instant lastPacketAt,
        String storagePolicy, String retryOf, boolean auto) {}
