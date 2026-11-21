package cn.tolim.pudans.contract.live;

import cn.tolim.pudans.contract.core.extension.live.PartState;
import java.time.Instant;

public record LiveRecordingPartView(
        int index, String filename, PartState state, Instant startedAt,
        Instant endedAt, long durationMillis, long size,
        int segmentCount, String errorCode, String message, boolean downloadable) {}
