package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.AgentSession;
import java.util.List;

public record AgentSessionPage(List<AgentSession> items, int limit) {}
