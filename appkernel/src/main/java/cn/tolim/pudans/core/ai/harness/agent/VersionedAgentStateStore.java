package cn.tolim.pudans.core.ai.harness.agent;

import reactor.core.publisher.Mono;

public interface VersionedAgentStateStore extends AgentStateStore {

    Mono<VersionedAgentState> loadVersioned(String userId, String sessionId);

    Mono<Long> saveIfVersion(String userId, String sessionId, AgentState state, long version);
}