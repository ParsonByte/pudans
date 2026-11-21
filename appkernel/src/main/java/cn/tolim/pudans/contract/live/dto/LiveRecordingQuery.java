package cn.tolim.pudans.contract.live.dto;

import cn.tolim.pudans.contract.live.resource.LiveRecordingState;
import java.time.Instant;
import java.util.List;

public record LiveRecordingQuery(
        String sourceName, List<LiveRecordingState> states,
        Instant from, Instant to, int page, int size) {}
