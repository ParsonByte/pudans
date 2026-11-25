package cn.tolim.pudans.contract.creation.dto;

import java.util.List;

public record GenerationTaskPage(List<GenerationTaskSummary> items, String nextCursor) {}
