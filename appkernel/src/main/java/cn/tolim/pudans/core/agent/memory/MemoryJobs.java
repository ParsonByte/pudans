package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryJob;
import cn.tolim.pudans.contract.agent.dto.MemoryJobCreateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryJobDetail;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobItemRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryJobs {

    public static final String CONVERSATION_EXTRACT = "conversation_extract";

    public static final String POOL_CONSOLIDATE = "pool_consolidate";

    private static final int MAX_LIMIT = 100;

    private final MemoryJobRepository jobs;

    private final MemoryJobItemRepository items;

    private final AgentSessionRepository sessions;

    public Mono<MemoryJob> create(RequestIdentity identity, MemoryJobCreateRequest request) {
        if (request == null || request.jobType() == null || request.jobType().isBlank()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-job-type-required", "任务类型不能为空。"));
        }
        var type = request.jobType().trim().toLowerCase();
        if (!CONVERSATION_EXTRACT.equals(type)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-job-type-invalid",
                    "用户只能创建会话记忆抽取任务。"));
        }
        if (request.sessionId() == null || request.runId() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-job-source-required",
                    "会话和运行标识不能为空。"));
        }
        return sessions
                .findByIdAndOwnerIdAndWorkspaceId(
                        request.sessionId(), identity.ownerId(), identity.workspaceId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "memory-session-not-found", "会话不存在。")))
                .flatMap(session -> {
                    var key = conversationKey(request.sessionId(), request.runId());
                    return jobs
                            .findByIdempotencyKey(key)
                            .switchIfEmpty(Mono.defer(() -> jobs.insert(queued(
                                    identity.ownerId(),
                                    request.sessionId(),
                                    request.runId(),
                                    key))))
                            .onErrorResume(error -> error instanceof ApiException api
                                            && "memory-job-conflict".equals(api.getCode()),
                                    error -> jobs.findByIdempotencyKey(key));
                })
                .map(MemoryJobEntity::toContract);
    }

    public Flux<MemoryJob> list(RequestIdentity identity, int requestedLimit) {
        return jobs.listVisible(identity.ownerId(), normalizeLimit(requestedLimit))
                .map(MemoryJobEntity::toContract);
    }

    public Flux<MemoryJob> listAll(int requestedLimit) {
        return jobs.listAll(normalizeLimit(requestedLimit)).map(MemoryJobEntity::toContract);
    }

    public Mono<MemoryJobDetail> detail(RequestIdentity identity, UUID id) {
        return jobs
                .findById(id)
                .filter(job -> "global".equals(job.getScopeType())
                        || identity.ownerId().equals(job.getScopeId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "memory-job-not-found", "记忆任务不存在。")))
                .flatMap(job -> items
                        .findByJobId(job.getId())
                        .map(entity -> entity.toContract())
                        .collectList()
                        .map(jobItems -> new MemoryJobDetail(job.toContract(), jobItems)));
    }

    public Mono<MemoryJobEntity> createDailyGlobal(LocalDate date) {
        var key = "daily:" + date + ":pool:global";
        return jobs
                .findByIdempotencyKey(key)
                .switchIfEmpty(Mono.defer(() -> {
                    var entity = new MemoryJobEntity();
                    entity.setId(UUID.randomUUID());
                    entity.setJobType(POOL_CONSOLIDATE);
                    entity.setScopeType("global");
                    entity.setScopeId(null);
                    entity.setStatus("queued");
                    entity.setIdempotencyKey(key);
                    entity.setCreatedAt(Instant.now());
                    return jobs.insert(entity);
                }))
                .onErrorResume(error -> error instanceof ApiException api
                                && "memory-job-conflict".equals(api.getCode()),
                        error -> jobs.findByIdempotencyKey(key));
    }

    public Mono<Void> fail(UUID id, String errorCode, String errorMessage) {
        return jobs.fail(id, errorCode, errorMessage);
    }

    private static int normalizeLimit(int requestedLimit) {
        return requestedLimit <= 0 ? 20 : Math.min(requestedLimit, MAX_LIMIT);
    }

    private static MemoryJobEntity queued(
            String ownerId, UUID sessionId, UUID runId, String idempotencyKey) {
        var entity = new MemoryJobEntity();
        entity.setId(UUID.randomUUID());
        entity.setJobType(CONVERSATION_EXTRACT);
        entity.setScopeType("owner");
        entity.setScopeId(ownerId);
        entity.setStatus("queued");
        entity.setIdempotencyKey(idempotencyKey);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private static String conversationKey(UUID sessionId, UUID runId) {
        return "session:" + sessionId + ":run:" + runId;
    }
}
