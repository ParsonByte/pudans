package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.AgentMessage;
import java.util.List;

public record AgentMessagePage(List<AgentMessage> items, String nextCursor) {}
