package cn.tolim.pudans.infra.r2dbc.agent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AgentMessageRepository extends ReactiveCrudRepository<AgentMessageEntity, UUID> {

    Flux<AgentMessageEntity> findBySessionIdOrderByCreatedAtDesc(UUID sessionId, Pageable pageable);

    Flux<AgentMessageEntity> findBySessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID sessionId, OffsetDateTime cursor, Pageable pageable);
}
