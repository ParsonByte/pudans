package cn.tolim.pudans.contract.ai;

import java.util.List;

public record AiPeakWindow(List<String> days, String start, String end, String zone) {}
