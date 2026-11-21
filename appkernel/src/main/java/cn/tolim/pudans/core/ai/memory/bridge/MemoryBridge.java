package cn.tolim.pudans.core.ai.memory.bridge;

import cn.tolim.pudans.core.ai.gateway.session.GatewaySessions;
import cn.tolim.pudans.core.ai.memory.MemoryRecall;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryBridge {

    private final GatewaySessions gatewaySessions;

    private final MemoryRecall memoryRecall;

    private final AiMemoryL1Repository l1Repository;

    private final AiMemoryL2Repository l2Repository;

    private final AiMessageRepository messageRepository;

    public Mono<List<MemoryInjection.Ref>> atomicSearch(
            String userId, String agentSource, String sessionKey, String query, int limit) {
        return session(userId, agentSource, sessionKey)
                .flatMap(session -> memoryRecall
                        .build(context(session), query, limit, 0)
                        .map(injection -> injection.refs().stream().limit(limit).toList()));
    }

    public Flux<Message> conversationSearch(
            String userId, String agentSource, String sessionKey, String query, int limit) {
        return session(userId, agentSource, sessionKey)
                .flatMapMany(session -> messageRepository.searchByScope(
                        session.teamId(), userId, session.agentId(), query, limit));
    }

    public Flux<L2SceneBlock> scenarios(String userId, String agentSource, String sessionKey) {
        return session(userId, agentSource, sessionKey)
                .flatMapMany(session -> l2Repository.findByScope(session.teamId(), session.agentId()));
    }

    public Mono<SceneDetail> readScenario(
            String userId, String agentSource, String sessionKey, String sceneKey) {
        return session(userId, agentSource, sessionKey).flatMap(session -> l2Repository
                .findByKey(session.teamId(), session.agentId(), sceneKey)
                .flatMap(scene -> l1Repository
                        .findByIds(scene.l1Ids())
                        .filter(memory -> session.teamId().equals(memory.context().teamId())
                                && session.agentId().equals(memory.context().agentId()))
                        .collectList()
                        .map(memories -> new SceneDetail(scene, memories))));
    }

    private Mono<GatewaySession> session(String userId, String agentSource, String sessionKey) {
        return gatewaySessions.requireSession(userId, agentSource, sessionKey);
    }

    private static MemoryContext context(GatewaySession session) {
        return new MemoryContext(
                session.teamId(), session.userId(), session.agentId(), session.sessionKey(), session.taskId());
    }

    public record SceneDetail(L2SceneBlock scene, List<L1MemoryRecord> memories) {

        public SceneDetail {
            memories = memories == null ? List.of() : List.copyOf(memories);
        }
    }
}
