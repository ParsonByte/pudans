package cn.tolim.pudans.infra.r2dbc.agent;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AgentToolCallRepository extends ReactiveCrudRepository<AgentToolCallEntity, UUID> {

    Flux<AgentToolCallEntity> findByRunIdOrderByStartedAtDesc(UUID runId);
}
