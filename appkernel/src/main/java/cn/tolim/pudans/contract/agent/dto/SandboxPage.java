package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.Sandbox;
import java.util.List;

public record SandboxPage(List<Sandbox> items, String nextCursor, int limit) {}
