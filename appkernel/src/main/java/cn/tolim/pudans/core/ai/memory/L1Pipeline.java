package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.memory.org.OrgCandidatePolicy;
import cn.tolim.pudans.core.ai.memory.org.OrgCandidateTaskPayload;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class L1Pipeline {

    private static final Logger log = LoggerFactory.getLogger(L1Pipeline.class);

    private final L1Deduplication l1Deduplication;

    private final MemoryPromptResolver promptResolver;

    private final AiMemoryL1Repository l1Repository;

    private final AiConversationRepository conversationRepository;

    private final AiMemoryTaskRepository taskRepository;

    private final AiMemoryGenerationLogRepository generationLogRepository;

    private final PersonaTrigger personaTrigger;

    private final @Nullable Embeddings embeddings;

    public L1Pipeline(
            L1Deduplication l1Deduplication,
            MemoryPromptResolver promptResolver,
            AiMemoryL1Repository l1Repository,
            AiConversationRepository conversationRepository,
            AiMemoryTaskRepository taskRepository,
            AiMemoryGenerationLogRepository generationLogRepository,
            PersonaTrigger personaTrigger,
            @Nullable Embeddings embeddings) {
        this.l1Deduplication = l1Deduplication;
        this.promptResolver = promptResolver;
        this.l1Repository = l1Repository;
        this.conversationRepository = conversationRepository;
        this.taskRepository = taskRepository;
        this.generationLogRepository = generationLogRepository;
        this.personaTrigger = personaTrigger;
        this.embeddings = embeddings;
    }

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Mono<Result> process(Request request, ConflictJudge conflictJudge) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(conflictJudge, "conflictJudge");
        var promptContext = MemoryPromptContext.compose(promptResolver, AiMemoryPrompt.Layer.L1, request.prompt());
        var memoriesById = flattenScenes(request.scenes());
        var candidatesByRecordId = candidateRecords(request.candidatePool());
        var existingMemories = request.candidatePool().stream()
                .filter(candidate -> candidate.status().isDedupCandidate())
                .map(candidate -> new L1Deduplication.ExistingMemory(
                        candidate.recordId(), candidate.content(), candidate.dedupHash(), candidate.status()))
                .toList();
        var dedupCandidates = memoriesById.values().stream()
                .map(memory -> new L1Deduplication.DedupCandidate(memory, existingMemories))
                .toList();
        var decisions = l1Deduplication.judgeConflicts(
                dedupCandidates, candidates -> conflictJudge.judge(promptContext.systemPrompt(), candidates));
        var now = clock.instant();
        return Flux.fromIterable(decisions)
                .concatMap(decision ->
                        applyDecision(request, decision, memoriesById, candidatesByRecordId, promptContext, now))
                .collectList()
                .flatMap(outcomes -> conversationRepository
                        .advanceL1Cursor(request.conversationId(), request.lastMessageId())
                        .map(advanced -> request.lastMessageId())
                        .flatMap(cursor -> scheduleTasks(request, outcomes, now)
                                .map(tasks -> result(outcomes, decisions, cursor, tasks))));
    }

    private Mono<Outcome> applyDecision(
            Request request,
            DedupDecision decision,
            Map<String, ExtractedMemory> memoriesById,
            Map<String, L1MemoryRecord> candidatesByRecordId,
            MemoryPromptContext promptContext,
            Instant now) {
        if (decision.action() == DedupAction.SKIP) {
            return Mono.just(new Outcome(null, 0));
        }
        var extracted = memoriesById.get(decision.recordId());
        if (extracted == null) {
            return Mono.error(new IllegalStateException("判定结果引用了未知记录: " + decision.recordId()));
        }
        var targetRecordIds = targetRecordIds(decision.targetIds());
        var timestamps = decision.mergedTimestamps().isEmpty()
                ? List.of(now)
                : decision.mergedTimestamps().stream().map(Instant::parse).toList();
        var content = hasText(decision.mergedContent()) ? decision.mergedContent() : extracted.content();
        var type = decision.mergedType() == null ? extracted.type() : decision.mergedType();
        var priority = decision.mergedPriority() == null ? extracted.priority() : decision.mergedPriority();
        var orgCandidate = orgCandidate(extracted, content, type, priority);
        var dedupHash = l1Deduplication.dedupHash(request.context(), content);
        var messageIds = messageIds(extracted.sourceMessageIds());
        var sameHashTarget = targetRecordIds.stream()
                .map(candidatesByRecordId::get)
                .filter(Objects::nonNull)
                .filter(target -> dedupHash.equals(target.dedupHash()))
                .findFirst();
        if (sameHashTarget.isPresent()) {
            var target = sameHashTarget.get();
            var updatedRecord = L1MemoryRecord.newRecord(
                    target.recordId(),
                    request.context(),
                    request.conversationId(),
                    content,
                    type,
                    priority,
                    extracted.sceneName(),
                    messageIds,
                    extracted.metadata(),
                    dedupHash,
                    request.scope(),
                    orgCandidate,
                    target.version() + 1,
                    timestamps);
            var remainingTargetIds = targetRecordIds.stream()
                    .filter(targetId -> !targetId.equals(target.recordId()))
                    .toList();
            return l1Repository
                    .updateExisting(updatedRecord)
                    .switchIfEmpty(Mono.error(
                            new IllegalStateException("待更新的 L1 记忆不存在: " + updatedRecord.recordId())))
                    .flatMap(updated -> supersede(decision, remainingTargetIds, updated.id())
                            .flatMap(superseded -> writeGenerationLog(request, promptContext, updated, superseded)));
        }
        var version = nextVersion(decision.action(), targetRecordIds, candidatesByRecordId);
        var record = L1MemoryRecord.newRecord(
                extracted.recordId(),
                request.context(),
                request.conversationId(),
                content,
                type,
                priority,
                extracted.sceneName(),
                messageIds,
                extracted.metadata(),
                dedupHash,
                request.scope(),
                orgCandidate,
                version,
                timestamps);
        return l1Repository
                .insert(record)
                .flatMap(inserted -> supersede(decision, targetRecordIds, inserted.id())
                        .flatMap(superseded -> writeGenerationLog(request, promptContext, inserted, superseded)));
    }

    private static boolean orgCandidate(
            ExtractedMemory extracted, String content, MemoryType type, int priority) {
        return OrgCandidatePolicy.isCandidate(new ExtractedMemory(
                extracted.recordId(),
                content,
                type,
                priority,
                extracted.sceneName(),
                extracted.sourceMessageIds(),
                extracted.metadata()));
    }

    private Mono<Outcome> writeGenerationLog(
            Request request, MemoryPromptContext promptContext, L1MemoryRecord record, long supersededCount) {
        return embed(record)
                .then(generationLogRepository.insert(promptContext.toGenerationLog(
                        record.id(),
                        request.context().teamId(),
                        request.context().agentId(),
                        request.context().userId(),
                        request.context().sessionId(),
                        request.generation())))
                .thenReturn(new Outcome(record, supersededCount));
    }

    // 向量写入是召回增强，失败时保留关键词召回能力
    private Mono<Void> embed(L1MemoryRecord record) {
        if (embeddings == null || record.id() == null) {
            return Mono.empty();
        }
        return embeddings
                .embed(List.of(record.content()))
                .filter(batch -> !batch.vectors().isEmpty())
                .flatMap(batch -> l1Repository.updateEmbedding(record.id(), batch.vectors().getFirst()))
                .then()
                .onErrorResume(error -> {
                    log.debug("L1 记忆 {} 向量写入失败：{}", record.id(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Long> supersede(DedupDecision decision, List<String> targetRecordIds, long supersededBy) {
        if (!decision.action().replacesExisting() || targetRecordIds.isEmpty()) {
            return Mono.just(0L);
        }
        return l1Repository.supersede(targetRecordIds, supersededBy);
    }

    private Mono<List<PipelineTaskType>> scheduleTasks(Request request, List<Outcome> outcomes, Instant now) {
        var tasks = tasksToSchedule(request, outcomes, now);
        if (tasks.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(tasks)
                .concatMap(taskRepository::insertIfAbsent)
                .thenMany(Flux.fromIterable(tasks))
                .map(MemoryPipelineTask::taskType)
                .collectList();
    }

    private List<MemoryPipelineTask> tasksToSchedule(Request request, List<Outcome> outcomes, Instant now) {
        var storedRecords = outcomes.stream()
                .map(Outcome::record)
                .filter(Objects::nonNull)
                .toList();
        if (storedRecords.isEmpty()) {
            return List.of();
        }
        var tasks = new ArrayList<MemoryPipelineTask>(3);
        if (request.l2Enabled()) {
            tasks.add(task(
                    PipelineTaskType.L2_SCENE,
                    "L2:" + request.context().teamId() + ":" + request.context().agentId(),
                    request,
                    now));
        }
        if (request.l3Enabled()
                && personaTrigger
                        .evaluate(personaStateAfterBatch(request.personaState(), storedRecords.size()))
                        .triggersGeneration()) {
            tasks.add(task(
                    PipelineTaskType.L3_PERSONA,
                    "L3:" + request.context().teamId() + ":" + request.context().agentId(),
                    request,
                    now));
        }
        scheduleOrgCandidate(request, storedRecords, tasks, now);
        return List.copyOf(tasks);
    }

    private static void scheduleOrgCandidate(
            Request request, List<L1MemoryRecord> storedRecords, List<MemoryPipelineTask> tasks, Instant now) {
        if (storedRecords.stream().noneMatch(L1MemoryRecord::orgCandidate)) {
            return;
        }
        var afterL1Id = storedRecords.stream()
                .map(L1MemoryRecord::id)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max();
        if (afterL1Id.isEmpty()) {
            return;
        }
        tasks.add(task(
                PipelineTaskType.ORG_CANDIDATE,
                "ORG:" + request.context().teamId() + ":" + request.context().agentId(),
                request,
                Map.of(OrgCandidateTaskPayload.AFTER_L1_ID, afterL1Id.getAsLong()),
                now));
    }

    private static PersonaTrigger.State personaStateAfterBatch(PersonaTrigger.State state, int storedCount) {
        return new PersonaTrigger.State(
                state.explicitRequested(),
                state.personaGenerated(),
                state.personaBodyPresent(),
                state.hasScenes(),
                state.scenesProcessed(),
                state.memoriesSinceLastPersona() + storedCount,
                state.threshold());
    }

    private static MemoryPipelineTask task(PipelineTaskType type, String dedupKey, Request request, Instant now) {
        return task(type, dedupKey, request, Map.of(), now);
    }

    private static MemoryPipelineTask task(
            PipelineTaskType type,
            String dedupKey,
            Request request,
            Map<String, Object> payload,
            Instant now) {
        return new MemoryPipelineTask(
                type,
                dedupKey,
                request.context(),
                payload,
                new MemoryPipelineTask.Schedule(request.conversationId(), 0, now));
    }

    private static Result result(
            List<Outcome> outcomes, List<DedupDecision> decisions, long cursor, List<PipelineTaskType> tasks) {
        var stored = outcomes.stream()
                .map(Outcome::record)
                .filter(Objects::nonNull)
                .toList();
        var superseded = outcomes.stream().mapToLong(Outcome::supersededCount).sum();
        return new Result(stored, decisions, cursor, superseded, tasks);
    }

    private static Map<String, ExtractedMemory> flattenScenes(List<SceneSegment> scenes) {
        var memories = new LinkedHashMap<String, ExtractedMemory>();
        for (var scene : scenes) {
            for (var memory : scene.memories()) {
                if (!scene.sceneName().equals(memory.sceneName())) {
                    throw new IllegalArgumentException("记忆场景与所属场景不一致");
                }
                if (memories.putIfAbsent(memory.recordId(), memory) != null) {
                    throw new IllegalArgumentException("重复的 recordId: " + memory.recordId());
                }
            }
        }
        return memories;
    }

    private static Map<String, L1MemoryRecord> candidateRecords(List<L1MemoryRecord> candidatePool) {
        var byRecordId = new LinkedHashMap<String, L1MemoryRecord>();
        for (var candidate : candidatePool) {
            if (candidate != null && candidate.status().isDedupCandidate()) {
                byRecordId.putIfAbsent(candidate.recordId(), candidate);
            }
        }
        return byRecordId;
    }

    private static List<String> targetRecordIds(List<String> targetIds) {
        for (var targetId : targetIds) {
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("targetIds 不能包含空白值");
            }
        }
        return List.copyOf(targetIds);
    }

    private static List<Long> messageIds(List<String> sourceMessageIds) {
        return sourceMessageIds.stream().map(Long::parseLong).toList();
    }

    private static int nextVersion(
            DedupAction action, List<String> targetRecordIds, Map<String, L1MemoryRecord> candidatesByRecordId) {
        if (action == DedupAction.STORE) {
            return 1;
        }
        return targetRecordIds.stream()
                        .map(candidatesByRecordId::get)
                        .filter(Objects::nonNull)
                        .mapToInt(L1MemoryRecord::version)
                        .max()
                        .orElse(1)
                + 1;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Outcome(L1MemoryRecord record, long supersededCount) {}

    @FunctionalInterface
    public interface ConflictJudge {
        List<DedupDecision> judge(String systemPrompt, List<L1Deduplication.DedupCandidate> candidates);
    }

    public record Request(
            MemoryContext context,
            long conversationId,
            List<SceneSegment> scenes,
            List<L1MemoryRecord> candidatePool,
            long lastMessageId,
            MemoryScope scope,
            boolean l2Enabled,
            boolean l3Enabled,
            PersonaTrigger.State personaState,
            MemoryPromptInput prompt,
            MemoryGenerationInput generation) {

        public Request {
            Objects.requireNonNull(context, "context");
            if (conversationId <= 0) {
                throw new IllegalArgumentException("conversationId 必须大于 0");
            }
            if (lastMessageId <= 0) {
                throw new IllegalArgumentException("lastMessageId 必须大于 0");
            }
            scenes = scenes == null ? List.of() : List.copyOf(scenes);
            candidatePool = candidatePool == null ? List.of() : List.copyOf(candidatePool);
            scope = scope == null ? MemoryScope.PRIVATE : scope;
            prompt = prompt == null ? MemoryPromptInput.none() : prompt;
            generation = generation == null ? MemoryGenerationInput.empty() : generation;
            if (l3Enabled && personaState == null) {
                throw new IllegalArgumentException("启用 L3 时必须提供 PersonaTrigger 状态");
            }
        }
    }

    public record Result(
            List<L1MemoryRecord> stored,
            List<DedupDecision> decisions,
            long l1Cursor,
            long supersededCount,
            List<PipelineTaskType> scheduledTasks) {

        public Result {
            stored = stored == null ? List.of() : List.copyOf(stored);
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
            scheduledTasks = scheduledTasks == null ? List.of() : List.copyOf(scheduledTasks);
        }
    }
}
