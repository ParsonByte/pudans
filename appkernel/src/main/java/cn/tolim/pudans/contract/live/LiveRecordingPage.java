package cn.tolim.pudans.contract.live;

import java.util.List;

public record LiveRecordingPage(
        List<LiveRecordingView> items, long total, int page, int size) {}
