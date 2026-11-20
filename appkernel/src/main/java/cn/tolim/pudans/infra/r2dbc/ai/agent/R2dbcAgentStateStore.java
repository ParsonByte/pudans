package cn.tolim.pudans.infra.r2dbc.ai.agent;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import cn.tolim.pudans.core.ai.harness.agent.VersionedAgentState;
import cn.tolim.pudans.core.ai.harness.agent.VersionedAgentStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcAgentStateStore implements VersionedAgentStateStore {

    private final AiAgentStateRepository repository;

    @Override
    public Mono<AgentState> load(String userId, String sessionId) {
        return loadVersioned(userId, sessionId).map(VersionedAgentState::state);
    }

    @Override
    public Mono<Void> save(String userId, String sessionId, AgentState state) {
        return repository.save(userId, sessionId, AgentStateCodec.encode(state)).then();
    }

    @Override
    public Mono<Void> delete(String userId, String sessionId) {
        return repository.delete(userId, sessionId);
    }

    @Override
    public Mono<VersionedAgentState> loadVersioned(String userId, String sessionId) {
        return repository.find(userId, sessionId)
                .map(row -> new VersionedAgentState(
                        AgentStateCodec.decode(row.state()),
                        row.version() == null ? 0 : row.version()));
    }

    @Override
    public Mono<Long> saveIfVersion(
            String userId, String sessionId, AgentState state, long version) {
        return repository.saveIfVersion(userId, sessionId, AgentStateCodec.encode(state), version);
    }
}