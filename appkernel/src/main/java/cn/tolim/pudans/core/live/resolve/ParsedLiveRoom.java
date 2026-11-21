package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.live.LivePlatform;

public record ParsedLiveRoom(LivePlatform platform, String roomId, String roomUrl) {}
