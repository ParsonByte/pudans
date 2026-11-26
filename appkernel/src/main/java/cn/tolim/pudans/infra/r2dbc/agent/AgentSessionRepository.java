package cn.tolim.pudans.infra.r2dbc.agent;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AgentSessionRepository extends ReactiveCrudRepository<AgentSessionEntity, UUID> {

    Flux<AgentSessionEntity> findByOwnerIdAndWorkspaceIdOrderByUpdatedAtDesc(
            String ownerId, String workspaceId, Pageable pageable);

    Flux<AgentSessionEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Mono<Long> countByOwnerIdAndWorkspaceId(String ownerId, String workspaceId);

    Mono<Long> countByOwnerIdAndWorkspaceIdAndStatus(String ownerId, String workspaceId, String status);

    Mono<Long> countByStatus(String status);

    Mono<AgentSessionEntity> findByIdAndOwnerIdAndWorkspaceId(
            UUID id, String ownerId, String workspaceId);

    Mono<AgentSessionEntity> findFirstByOwnerIdAndWorkspaceIdOrderByLastMessageAtDesc(
            String ownerId, String workspaceId);

    Mono<AgentSessionEntity> findFirstByOrderByLastMessageAtDesc();
}
