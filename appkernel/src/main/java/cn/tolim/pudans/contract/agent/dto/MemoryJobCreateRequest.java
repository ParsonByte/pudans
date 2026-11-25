package cn.tolim.pudans.contract.agent.dto;

import java.util.UUID;

public record MemoryJobCreateRequest(String jobType, UUID sessionId, UUID runId) {}
