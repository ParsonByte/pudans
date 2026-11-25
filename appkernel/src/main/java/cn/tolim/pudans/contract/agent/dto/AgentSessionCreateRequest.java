package cn.tolim.pudans.contract.agent.dto;

import java.util.UUID;

public record AgentSessionCreateRequest(String title, String profile, UUID defaultModelId) {}
