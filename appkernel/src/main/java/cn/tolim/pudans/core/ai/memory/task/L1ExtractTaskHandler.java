package cn.tolim.pudans.core.ai.memory.task;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.conversation.Conversations;
import cn.tolim.pudans.core.ai.memory.DedupDecision;
import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.L1Deduplication;
import cn.tolim.pudans.core.ai.memory.L1Deduplication.DedupCandidate;
import cn.tolim.pudans.core.ai.memory.L1Deduplication.ExistingMemory;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L1Pipeline;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryGenerationInput;
import cn.tolim.pudans.core.ai.memory.MemoryPromptInput;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.PersonaTrigger;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.ai.memory.SceneSegment;
import cn.tolim.pudans.core.ai.memory.ai.ExtractionOutcome;
import cn.tolim.pudans.core.ai.memory.ai.MemoryConflict;
import cn.tolim.pudans.core.ai.memory.ai.MemoryExtraction;
import cn.tolim.pudans.core.ai.memory.org.OrgCandidatePolicy;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.core.ai.task.AiTaskHandler;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class L1ExtractTaskHandler implements AiTaskHandler {

    public static final String TASK_TYPE = PipelineTaskType.L1_EXTRACT.name();

    private static final int DEFAULT_MAX_MESSAGES = 10;

    private static final int MAX_MESSAGES = 50;

    private static final int CANDIDATE_POOL_LIMIT = 100;

    private static final int MAX_BATCHES = 10;

    private final AiConversationRepository conversationRepository;

    private final Conversations conversations;

    private final AiMessageRepository messageRepository;

    private final AiMemoryL1Repository memoryL1Repository;

    private final AiMemoryL2Repository memoryL2Repository;

    private final AiMemoryL3Repository memoryL3Repository;

    private final AiSettings aiSettings;

    private final MemoryExtraction extractionAi;

    private final MemoryConflict conflictAi;

    private final L1Deduplication l1Deduplication;

    private final L1Pipeline l1Pipeline;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Mono<Void> handle(AiTask task) {
        var conversationId = MemoryTaskPayloads.conversationId(task);
        if (conversationId == null) {
            return Mono.error(new IllegalArgumentException("L1 任务缺少会话 ID"));
        }
        return conversationRepository
                .findById(conversationId)
                .flatMap(conversation -> extract(task, conversation))
                .then();
    }

    private Mono<Void> extract(AiTask task, Conversation conversation) {
        return aiSettings.spec().flatMap(setting -> {
            var memory = setting.getMemory() == null ? new AiSetting.Memory() : setting.getMemory();
            var afterCursor = conversation.l1CursorMessageId() == null ? 0L : conversation.l1CursorMessageId();
            return extractBatches(task, conversation, memory, afterCursor, 0);
        });
    }

    private Mono<Void> extractBatches(
            AiTask task, Conversation conversation, AiSetting.Memory memory, long afterCursor, int batchIndex) {
        var limit = extractionLimit(memory.getMaxMessagesPerExtraction());
        return messageRepository
                .listMessages(conversation.id(), afterCursor, limit)
                .collectList()
                .flatMap(messages -> messages.isEmpty()
                        ? Mono.<Void>empty()
                        : processBatch(task, conversation, memory, messages)
                                .then(hasMoreBatches(messages.size(), limit, batchIndex)
                                        ? extractBatches(
                                                task,
                                                conversation,
                                                memory,
                                                messages.getLast().id(),
                                                batchIndex + 1)
                                        : Mono.<Void>empty()));
    }

    private Mono<Void> processBatch(
            AiTask task, Conversation conversation, AiSetting.Memory memory, List<Message> messages) {
        var context = new MemoryContext(
                conversation.teamId(),
                conversation.userId(),
                conversation.agentId(),
                conversation.sessionKey(),
                String.valueOf(task.id()));
        return extractionAi
                .extract(context, conversation.id(), messages)
                .flatMap(outcome -> apply(conversation, memory, messages, context, outcome));
    }

    private static boolean hasMoreBatches(int messageCount, int limit, int batchIndex) {
        return batchIndex + 1 < MAX_BATCHES && messageCount >= limit;
    }

    private Mono<Void> apply(
            Conversation conversation,
            AiSetting.Memory memory,
            List<Message> messages,
            MemoryContext context,
            ExtractionOutcome outcome) {
        return memoryL1Repository
                .listByUser(
                        conversation.teamId(),
                        conversation.agentId(),
                        conversation.userId(),
                        null,
                        Long.MAX_VALUE,
                        CANDIDATE_POOL_LIMIT)
                .collectList()
                .flatMap(pool -> conflictDecisions(context, outcome.scenes(), pool)
                        .flatMap(decisions ->
                                runPipeline(conversation, memory, messages, context, outcome, pool, decisions))
                        .then())
                .then(conversations.reducePending(conversation, messages.size()).then());
    }

    private Mono<List<DedupDecision>> conflictDecisions(
            MemoryContext context, List<SceneSegment> scenes, List<L1MemoryRecord> pool) {
        var existing = existingMemories(pool);
        var candidates = new ArrayList<DedupCandidate>();
        for (var scene : scenes) {
            for (var memory : scene.memories()) {
                candidates.add(new DedupCandidate(memory, prioritized(context, memory, existing)));
            }
        }
        if (candidates.isEmpty()) {
            return Mono.just(List.<DedupDecision>of());
        }
        return conflictAi.judge("", List.copyOf(candidates)).defaultIfEmpty(List.<DedupDecision>of());
    }

    private List<ExistingMemory> prioritized(
            MemoryContext context, ExtractedMemory memory, List<ExistingMemory> existing) {
        var hash = l1Deduplication.dedupHash(context, memory.content());
        return Stream.concat(
                        existing.stream().filter(candidate -> hash.equals(candidate.dedupHash())),
                        existing.stream().filter(candidate -> !hash.equals(candidate.dedupHash())))
                .toList();
    }

    private static List<ExistingMemory> existingMemories(List<L1MemoryRecord> pool) {
        return pool.stream()
                .filter(record -> record.status().isDedupCandidate())
                .map(record -> new ExistingMemory(
                        record.recordId(), record.content(), record.dedupHash(), record.status()))
                .toList();
    }

    private Mono<L1Pipeline.Result> runPipeline(
            Conversation conversation,
            AiSetting.Memory memory,
            List<Message> messages,
            MemoryContext context,
            ExtractionOutcome outcome,
            List<L1MemoryRecord> pool,
            List<DedupDecision> decisions) {
        var lastMessageId = messages.getLast().id();
        var persona = memoryL3Repository
                .findByScope(conversation.teamId(), conversation.agentId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
        var scenes = memoryL2Repository
                .findByScope(conversation.teamId(), conversation.agentId())
                .collectList();
        return Mono.zip(persona, scenes).flatMap(loaded -> {
            var current = loaded.getT1();
            var existingScenes = loaded.getT2();
            var state = new PersonaTrigger.State(
                    false,
                    current.isPresent(),
                    current.map(L3Persona::personaText).map(L1ExtractTaskHandler::hasText).orElse(false),
                    !existingScenes.isEmpty(),
                    existingScenes.size(),
                    pool.size(),
                    MemoryTaskPayloads.PERSONA_THRESHOLD);
            var orgCandidate = OrgCandidatePolicy.hasCandidate(outcome.scenes());
            var request = new L1Pipeline.Request(
                    context,
                    conversation.id(),
                    outcome.scenes(),
                    pool,
                    lastMessageId,
                    MemoryScope.PRIVATE,
                    orgCandidate,
                    memory.isL2Enabled(),
                    memory.isL3Enabled(),
                    state,
                    MemoryPromptInput.none(),
                    new MemoryGenerationInput(
                            outcome.modelCode(),
                            "conversation:" + conversation.id(),
                            "l1:" + lastMessageId));
            return l1Pipeline.process(request, (systemPrompt, candidates) -> decisions);
        });
    }

    private static int extractionLimit(int configured) {
        if (configured <= 0) {
            return DEFAULT_MAX_MESSAGES;
        }
        return Math.min(configured, MAX_MESSAGES);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
