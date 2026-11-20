package cn.tolim.pudans.core.ai.harness.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import reactor.core.publisher.Mono;

public final class InMemoryAgentStateStore implements AgentStateStore {

    private final ConcurrentMap<String, AgentState> states = new ConcurrentHashMap<>();

    @Override
    public Mono<AgentState> load(String userId, String sessionId) {
        return Mono.justOrEmpty(states.get(key(userId, sessionId))).map(AgentState::copy);
    }

    @Override
    public Mono<Void> save(String userId, String sessionId, AgentState state) {
        return Mono.fromRunnable(() -> states.put(key(userId, sessionId), state.copy()))
                .then();
    }

    @Override
    public Mono<Void> delete(String userId, String sessionId) {
        return Mono.fromRunnable(() -> states.remove(key(userId, sessionId))).then();
    }

    private static String key(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }
}