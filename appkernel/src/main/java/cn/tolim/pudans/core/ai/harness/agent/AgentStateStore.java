package cn.tolim.pudans.core.ai.harness.agent;

import reactor.core.publisher.Mono;

public interface AgentStateStore {

    Mono<AgentState> load(String userId, String sessionId);

    Mono<Void> save(String userId, String sessionId, AgentState state);

    default Mono<Void> delete(String userId, String sessionId) {
        return Mono.empty();
    }
}