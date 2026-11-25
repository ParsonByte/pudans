package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.MemoryJob;
import cn.tolim.pudans.contract.agent.MemoryJobItem;
import java.util.List;

public record MemoryJobDetail(MemoryJob job, List<MemoryJobItem> items) {}
