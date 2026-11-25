package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.MemoryPoolItem;
import java.util.List;

public record MemoryPoolList(List<MemoryPoolItem> items) {}
