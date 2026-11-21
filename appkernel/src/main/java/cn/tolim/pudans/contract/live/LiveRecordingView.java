package cn.tolim.pudans.contract.live;

import cn.tolim.pudans.contract.core.extension.live.LivePlatform;
import cn.tolim.pudans.contract.core.extension.live.LiveRecordingState;
import java.time.Instant;

public record LiveRecordingView(
        String name, String sourceName, LivePlatform platform, String roomId,
        String roomTitle, String anchorName, String format,
        LiveRecordingState state, String errorCode, Instant startedAt,
        Instant endedAt, long durationMillis, long totalBytes,
        int segmentCount, int partCount, long localBytes,
        String storagePolicy, String retryOf, boolean auto) {}
