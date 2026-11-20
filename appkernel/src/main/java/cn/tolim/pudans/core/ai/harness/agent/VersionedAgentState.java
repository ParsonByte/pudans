package cn.tolim.pudans.core.ai.harness.agent;

public record VersionedAgentState(AgentState state, long version) {}