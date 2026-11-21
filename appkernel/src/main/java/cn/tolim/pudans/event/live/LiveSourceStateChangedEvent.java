package cn.tolim.pudans.event.live;

import java.time.Instant;
import cn.tolim.pudans.contract.live.resource.LiveSourceState;

public record LiveSourceStateChangedEvent(
        String sourceName,
        String roomId,
        LiveSourceState from,
        LiveSourceState to,
        String reason,
        Instant at) {}
