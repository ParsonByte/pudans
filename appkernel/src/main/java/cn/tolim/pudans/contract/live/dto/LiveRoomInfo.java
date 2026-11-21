package cn.tolim.pudans.contract.live.dto;

import java.time.Instant;
import java.util.List;

public record LiveRoomInfo(
        String roomId,
        String title,
        String anchorName,
        String anchorAvatar,
        String anchorId,
        boolean live,
        String liveStatus,
        List<LiveStreamUrl> streams,
        Instant resolvedAt) {}
