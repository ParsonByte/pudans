package cn.tolim.pudans.infra.r2dbc.agent;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AgentRunRepository extends ReactiveCrudRepository<AgentRunEntity, UUID> {

    Mono<AgentRunEntity> findByIdAndSessionId(UUID id, UUID sessionId);
}
