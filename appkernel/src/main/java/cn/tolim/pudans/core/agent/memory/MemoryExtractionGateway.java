package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface MemoryExtractionGateway {

    Mono<List<MemoryExtractionResult>> extract(
            AgentSessionEntity session, UUID runId, List<AgentMessageEntity> messages);
}
