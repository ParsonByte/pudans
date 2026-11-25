package cn.tolim.pudans.contract.creation.dto;

import cn.tolim.pudans.contract.creation.Work;
import java.util.List;

public record WorkRelated(List<Work> items, String emptyState) {}
