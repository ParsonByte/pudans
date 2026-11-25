package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.SandboxOperation;
import java.util.List;

public record SandboxOperationPage(List<SandboxOperation> items, String nextCursor, int limit) {}
