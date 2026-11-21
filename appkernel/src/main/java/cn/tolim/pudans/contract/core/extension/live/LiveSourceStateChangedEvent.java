package cn.tolim.pudans.contract.core.extension.live;

import java.time.Instant;

public record LiveSourceStateChangedEvent(
        String sourceName,
        String roomId,
        LiveSourceState from,
        LiveSourceState to,
        String reason,
        Instant at) {}
