package cn.tolim.pudans.contract.agent.dto;

import java.time.Instant;

public record AgentSessionSummary(long total, long active, Instant latestMessageAt) {}
