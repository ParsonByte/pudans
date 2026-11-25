package cn.tolim.pudans.infra.r2dbc.agent;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AgentApprovalRepository extends ReactiveCrudRepository<AgentApprovalEntity, UUID> {

    Mono<AgentApprovalEntity> findByIdAndRunId(UUID id, UUID runId);

    Mono<AgentApprovalEntity> findFirstByRunIdAndToolCallIdAndStatus(
            UUID runId, UUID toolCallId, String status);
}
