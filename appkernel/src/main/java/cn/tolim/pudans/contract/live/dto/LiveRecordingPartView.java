package cn.tolim.pudans.contract.live.dto;

import cn.tolim.pudans.contract.live.resource.PartState;
import java.time.Instant;

public record LiveRecordingPartView(
        int index, String filename, PartState state, Instant startedAt,
        Instant endedAt, long durationMillis, long size,
        int segmentCount, String errorCode, String message, boolean downloadable) {}
