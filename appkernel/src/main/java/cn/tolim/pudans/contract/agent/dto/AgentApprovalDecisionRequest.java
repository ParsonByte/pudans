package cn.tolim.pudans.contract.agent.dto;

import java.util.Map;

public record AgentApprovalDecisionRequest(String decision, Map<String, Object> details) {}
