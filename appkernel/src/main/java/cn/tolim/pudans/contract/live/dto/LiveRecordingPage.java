package cn.tolim.pudans.contract.live.dto;

import java.util.List;

public record LiveRecordingPage(
        List<LiveRecordingView> items, long total, int page, int size) {}
