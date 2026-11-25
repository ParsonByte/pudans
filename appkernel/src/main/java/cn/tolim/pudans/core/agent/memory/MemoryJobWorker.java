package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryItem;
import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddingModels;
import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddings;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobItemEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobItemRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.vector.MemoryVectorRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryJobWorker {

    private final MemoryJobRepository jobs;

    private final MemoryJobItemRepository jobItems;

    private final MemorySources sources;

    private final MemoryExtractionGateway extraction;

    private final PersonalMemories memories;

    private final MemoryEmbeddingModels embeddingModels;

    private final MemoryEmbeddings embeddings;

    private final MemoryVectorRepository vectors;

    private final MemoryEvents events;

    private final MemoryPooling pooling;

    private final MemoryAudit audit;

    public void start(MemoryJobEntity job) {
        subscribe(process(job), job.getId());
    }

    public void start(UUID jobId) {
        subscribe(jobs.findById(jobId).flatMap(this::process), jobId);
    }

    private void subscribe(Mono<Void> execution, UUID jobId) {
        execution
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> {},
                        error -> log.error("记忆任务启动失败：job={}", jobId, error));
    }

    public Mono<Void> process(MemoryJobEntity job) {
        return jobs.markRunning(job.getId(), Instant.now()).flatMap(claimed -> {
            if (!Boolean.TRUE.equals(claimed)) {
                return Mono.empty();
            }
            return switch (job.getJobType()) {
                case MemoryJobs.CONVERSATION_EXTRACT -> conversation(job);
                case MemoryJobs.POOL_CONSOLIDATE -> pool(job);
                default -> fail(job, "memory-job-type-invalid", "不支持的记忆任务类型。");
            };
        }).onErrorResume(error -> fail(job, code(error), message(error)));
    }

    private Mono<Void> conversation(MemoryJobEntity job) {
        var key = conversationKey(job.getIdempotencyKey());
        return sources
                .registerRun(job.getScopeId(), key.sessionId(), key.runId())
                .flatMap(conversation -> {
                    var identity = new RequestIdentity(
                            conversation.session().getOwnerId(),
                            conversation.session().getWorkspaceId());
                    return stage(
                                    job.getId(),
                                    conversation.source().getId().toString(),
                                    "source_index",
                                    "completed")
                            .then(extraction.extract(
                                    conversation.session(), key.runId(), conversation.messages()))
                            .flatMap(results -> {
                                var total = results.size() + 1;
                                var processed = new AtomicInteger(1);
                                var failed = new AtomicInteger();
                                return jobs.updateProgress(
                                                job.getId(), total, processed.get(), failed.get())
                                        .thenMany(Flux.fromIterable(results)
                                                .concatMap(result -> processResult(
                                                        job,
                                                        identity,
                                                        conversation.source().getId(),
                                                        result,
                                                        processed,
                                                        failed,
                                                        total)))
                                        .then(Mono.defer(() -> jobs.complete(
                                                job.getId(),
                                                total,
                                                processed.get(),
                                                failed.get())))
                                        .doOnSuccess(ignored -> audit.background(
                                                identity.ownerId(),
                                                "MEMORY_JOB_COMPLETED",
                                                "MemoryJob",
                                                job.getId(),
                                                true,
                                                Map.of(
                                                        "processedItems", processed.get(),
                                                        "failedItems", failed.get())));
                            });
                });
    }

    private Mono<Void> processResult(
            MemoryJobEntity job,
            RequestIdentity identity,
            UUID sourceId,
            MemoryExtractionResult result,
            AtomicInteger processed,
            AtomicInteger failed,
            int total) {
        var evidence = effectiveEvidence(result, sourceId);
        return memories
                .extract(identity, new PersonalMemories.ExtractedMemory(
                        result.layer(),
                        result.memoryType(),
                        result.canonicalKey(),
                        result.content(),
                        result.summary(),
                        evidence,
                        List.of(sourceId.toString()),
                        result.confidence()))
                .flatMap(memory -> {
                    processed.incrementAndGet();
                    return stage(
                                    job.getId(),
                                    memory.id().toString(),
                                    "l1_extract",
                                    "completed")
                            .then(events.publish(
                                    identity,
                                    MemoryEvents.EXTRACTED,
                                    memory.id(),
                                    memory.layer(),
                                    memory.status(),
                                    Map.of("memoryType", memory.memoryType())))
                            .then(indexVector(job, identity, memory, failed))
                            .then(jobs.updateProgress(
                                    job.getId(), total, processed.get(), failed.get()));
                });
    }

    private Mono<Void> indexVector(
            MemoryJobEntity job,
            RequestIdentity identity,
            MemoryItem memory,
            AtomicInteger failed) {
        return embeddingModels
                .defaultSpace(identity.workspaceId())
                .flatMap(space -> embeddings
                        .embed(space, memory.content())
                        .flatMap(vector -> vectors.upsert(
                                space.vectorSpace(),
                                "personal",
                                identity.ownerId(),
                                identity.workspaceId(),
                                memory.id(),
                                memory.layer(),
                                vector,
                                memory.contentHash(),
                                Instant.now())))
                .onErrorResume(error -> notConfigured(error)
                        ? Mono.empty()
                        : stage(
                                        job.getId(),
                                        memory.id().toString(),
                                        "vector_index",
                                        "failed")
                                .doOnSuccess(ignored -> failed.incrementAndGet())
                                .then());
    }

    private Mono<Void> pool(MemoryJobEntity job) {
        return stage(job.getId(), null, "pool_consolidate", "running")
                .flatMap(item -> pooling.consolidate().flatMap(result -> {
                    var total = Math.max(1, result.scanned());
                    var processed = Math.min(
                            total, result.created() + result.updated() + result.skipped());
                    return jobItems
                            .update(
                                    item.getId(),
                                    "completed",
                                    null,
                                    null,
                                    Instant.now())
                            .then(jobs.complete(job.getId(), total, processed, 0))
                            .doOnSuccess(ignored -> audit.background(
                                    "system",
                                    "MEMORY_POOL_CONSOLIDATED",
                                    "MemoryJob",
                                    job.getId(),
                                    true,
                                    Map.of(
                                            "created", result.created(),
                                            "updated", result.updated(),
                                            "skipped", result.skipped())));
                })).then();
    }

    private Mono<MemoryJobItemEntity> stage(
            UUID jobId, String sourceId, String stage, String status) {
        var now = Instant.now();
        var entity = new MemoryJobItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setJobId(jobId);
        entity.setSourceId(sourceId);
        entity.setStage(stage);
        entity.setStatus(status);
        entity.setAttempt(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return jobItems.insert(entity);
    }

    private Mono<Void> fail(MemoryJobEntity job, String code, String message) {
        var normalizedCode = "memory-extraction-model-not-configured".equals(code)
                ? "model-not-configured"
                : code;
        return jobs.fail(job.getId(), normalizedCode, message)
                .thenMany(stage(job.getId(), null, "job_failure", "failed"))
                .then();
    }

    private static List<Map<String, Object>> effectiveEvidence(
            MemoryExtractionResult result, UUID sourceId) {
        var evidence = new java.util.ArrayList<Map<String, Object>>();
        var sourceReference = new LinkedHashMap<String, Object>();
        sourceReference.put("type", "memory_source");
        sourceReference.put("sourceId", sourceId.toString());
        evidence.add(sourceReference);
        if (result.evidenceMessageIds() != null) {
            for (var messageId : result.evidenceMessageIds()) {
                var reference = new LinkedHashMap<String, Object>();
                reference.put("type", "agent_message");
                reference.put("messageId", messageId.toString());
                evidence.add(reference);
            }
        }
        return List.copyOf(evidence);
    }

    private static ConversationKey conversationKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "memory-job-key-invalid",
                    "记忆抽取任务缺少幂等键。");
        }
        var parts = idempotencyKey.split(":");
        if (parts.length != 4
                || !"session".equals(parts[0])
                || !"run".equals(parts[2])) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "memory-job-key-invalid",
                    "记忆抽取任务幂等键不合法。");
        }
        try {
            return new ConversationKey(UUID.fromString(parts[1]), UUID.fromString(parts[3]));
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "memory-job-key-invalid",
                    "记忆抽取任务幂等键不合法。",
                    error);
        }
    }

    private static boolean notConfigured(Throwable error) {
        if (!(error instanceof ApiException api)) {
            return false;
        }
        return api.getCode().startsWith("memory-embedding")
                || api.getCode().startsWith("memory-vector-space")
                || "ai-model-default-missing".equals(api.getCode())
                || "ai-model-unavailable".equals(api.getCode());
    }

    private static String code(Throwable error) {
        return error instanceof ApiException api ? api.getCode() : "memory-job-failed";
    }

    private static String message(Throwable error) {
        return error instanceof ApiException api ? api.getMessage() : error.getMessage();
    }

    private record ConversationKey(UUID sessionId, UUID runId) {}
}
