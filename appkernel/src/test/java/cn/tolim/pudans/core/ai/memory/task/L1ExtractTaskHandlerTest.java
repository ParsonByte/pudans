package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.memory.DedupDecision;
import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.L1DedupService;
import cn.tolim.pudans.core.ai.memory.L1DedupService.DedupCandidate;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L1PipelineService;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryPromptInput;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.SceneSegment;
import cn.tolim.pudans.core.ai.memory.ai.ExtractionOutcome;
import cn.tolim.pudans.core.ai.memory.ai.MemoryConflictAiService;
import cn.tolim.pudans.core.ai.memory.ai.MemoryExtractionAiService;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class L1ExtractTaskHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final long CONVERSATION_ID = 9L;

    private static final long TASK_ID = 5L;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiConversationService conversationService;

    @Mock
    AiMessageRepository messageRepository;

    @Mock
    AiMemoryL1Repository memoryL1Repository;

    @Mock
    AiMemoryL2Repository memoryL2Repository;

    @Mock
    AiMemoryL3Repository memoryL3Repository;

    @Mock
    AiSettingService settingService;

    @Mock
    MemoryExtractionAiService extractionAi;

    @Mock
    MemoryConflictAiService conflictAi;

    @Mock
    L1PipelineService pipelineService;

    private L1DedupService dedupService;

    private L1ExtractTaskHandler handler;

    @BeforeEach
    void setUp() {
        dedupService = new L1DedupService();
        handler = new L1ExtractTaskHandler(
                conversationRepository,
                conversationService,
                messageRepository,
                memoryL1Repository,
                memoryL2Repository,
                memoryL3Repository,
                settingService,
                extractionAi,
                conflictAi,
                dedupService,
                pipelineService);
    }

    @Test
    void extractsMessagesAndReducesPendingCount() {
        var messages = List.of(message(101L), message(102L));
        var extracted = extracted("m-1", "用户偏好暗色主题");
        var decisions = List.of(DedupDecision.store("m-1"));
        stubConversation(null);
        stubSetting();
        when(messageRepository.listMessages(CONVERSATION_ID, 0L, 10)).thenReturn(Flux.fromIterable(messages));
        when(extractionAi.extract(any(), eq(CONVERSATION_ID), eq(messages)))
                .thenReturn(Mono.just(new ExtractionOutcome(
                        List.of(new SceneSegment("偏好", List.of("101"), List.of(extracted))), "model-a")));
        when(memoryL1Repository.listByUser(
                        "team-a", "agent-a", "user-1", null, Long.MAX_VALUE, 100))
                .thenReturn(Flux.empty());
        when(conflictAi.judge(eq(""), anyList())).thenReturn(Mono.just(decisions));
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.empty());
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.empty());
        when(pipelineService.process(any(), any())).thenReturn(Mono.just(result(102L)));
        when(conversationService.reducePending(conversation(null, null), 2)).thenReturn(Mono.just(2));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        var contextCaptor = ArgumentCaptor.forClass(MemoryContext.class);
        verify(extractionAi).extract(contextCaptor.capture(), eq(CONVERSATION_ID), eq(messages));
        var context = contextCaptor.getValue();
        assertThat(context.teamId()).isEqualTo("team-a");
        assertThat(context.userId()).isEqualTo("user-1");
        assertThat(context.agentId()).isEqualTo("agent-a");
        assertThat(context.sessionId()).isEqualTo("session-1");
        assertThat(context.taskId()).isEqualTo(String.valueOf(TASK_ID));

        var requestCaptor = ArgumentCaptor.forClass(L1PipelineService.Request.class);
        var judgeCaptor = ArgumentCaptor.forClass(L1PipelineService.ConflictJudge.class);
        verify(pipelineService).process(requestCaptor.capture(), judgeCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.context()).isEqualTo(contextCaptor.getValue());
        assertThat(request.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(request.lastMessageId()).isEqualTo(102L);
        assertThat(request.scope()).isEqualTo(MemoryScope.PRIVATE);
        assertThat(request.orgCandidate()).isFalse();
        assertThat(request.l2Enabled()).isTrue();
        assertThat(request.l3Enabled()).isTrue();
        assertThat(request.prompt()).isEqualTo(MemoryPromptInput.none());
        assertThat(request.generation().modelCode()).isEqualTo("model-a");
        assertThat(request.generation().inputRef()).isEqualTo("conversation:9");
        assertThat(request.generation().outputRef()).isEqualTo("l1:102");
        assertThat(request.personaState().memoriesSinceLastPersona()).isZero();
        assertThat(judgeCaptor.getValue().judge("ignored", List.of())).isEqualTo(decisions);

        verify(conversationService).reducePending(conversation(null, null), 2);
    }

    @Test
    void processesRemainingBatchesAfterAFullBatch() {
        var firstBatch = List.of(message(101L), message(102L));
        var secondBatch = List.of(message(103L));
        var extracted = extracted("m-1", "用户偏好暗色主题");
        var decisions = List.of(DedupDecision.store("m-1"));
        stubConversation(null);
        stubSetting(2);
        when(messageRepository.listMessages(CONVERSATION_ID, 0L, 2)).thenReturn(Flux.fromIterable(firstBatch));
        when(messageRepository.listMessages(CONVERSATION_ID, 102L, 2)).thenReturn(Flux.fromIterable(secondBatch));
        when(extractionAi.extract(any(), eq(CONVERSATION_ID), anyList()))
                .thenReturn(Mono.just(new ExtractionOutcome(
                        List.of(new SceneSegment("偏好", List.of("101"), List.of(extracted))), "model-a")));
        when(memoryL1Repository.listByUser(
                        "team-a", "agent-a", "user-1", null, Long.MAX_VALUE, 100))
                .thenReturn(Flux.empty());
        when(conflictAi.judge(eq(""), anyList())).thenReturn(Mono.just(decisions));
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.empty());
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.empty());
        when(pipelineService.process(any(), any()))
                .thenReturn(Mono.just(result(102L)), Mono.just(result(103L)));
        when(conversationService.reducePending(conversation(null, null), 2)).thenReturn(Mono.just(2));
        when(conversationService.reducePending(conversation(null, null), 1)).thenReturn(Mono.just(1));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verify(messageRepository).listMessages(CONVERSATION_ID, 0L, 2);
        verify(messageRepository).listMessages(CONVERSATION_ID, 102L, 2);
        verify(extractionAi, times(2)).extract(any(), eq(CONVERSATION_ID), anyList());
        verify(conversationService, times(2)).reducePending(eq(conversation(null, null)), anyInt());
    }

    @Test
    void completesSilentlyWhenConversationIsMissing() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(messageRepository, extractionAi, conflictAi, pipelineService);
        verify(conversationService, never()).reducePending(any(), anyInt());
    }

    @Test
    void completesSilentlyWhenNoMessagesRemain() {
        stubConversation(null);
        stubSetting();
        when(messageRepository.listMessages(CONVERSATION_ID, 0L, 10)).thenReturn(Flux.empty());

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        verifyNoInteractions(extractionAi, conflictAi, pipelineService);
        verify(conversationService, never()).reducePending(any(), anyInt());
    }

    @Test
    void precomputesConflictDecisionsAndPersonaState() {
        var existingHash = dedupService.dedupHash(context(), "用户偏好暗色主题");
        var exact = record("m-old", "用户偏好暗色主题", existingHash);
        var other = record("m-other", "项目使用 Gradle 构建", "other-hash");
        var extracted = extracted("m-1", "用户偏好暗色主题");
        var decisions = List.of(DedupDecision.store("m-1"));
        var persona = new L3Persona(
                null, "team-a", "agent-a", Map.of(), "现有画像", 3, 2, BigDecimal.ONE, NOW);
        var scene = new L2SceneBlock(
                null,
                "team-a",
                "agent-a",
                "scene-1",
                "场景一",
                "场景摘要",
                List.of(55L),
                1,
                BigDecimal.ONE,
                NOW);
        stubConversation(null);
        stubSetting();
        when(messageRepository.listMessages(CONVERSATION_ID, 0L, 10)).thenReturn(Flux.just(message(101L)));
        when(extractionAi.extract(any(), eq(CONVERSATION_ID), anyList()))
                .thenReturn(Mono.just(new ExtractionOutcome(
                        List.of(new SceneSegment("偏好", List.of("101"), List.of(extracted))), "model-a")));
        when(memoryL1Repository.listByUser(
                        "team-a", "agent-a", "user-1", null, Long.MAX_VALUE, 100))
                .thenReturn(Flux.just(exact, other));
        var capturedCandidates = new AtomicReference<List<DedupCandidate>>();
        when(conflictAi.judge(eq(""), anyList())).thenAnswer(invocation -> {
            capturedCandidates.set(invocation.getArgument(1));
            return Mono.just(decisions);
        });
        when(memoryL3Repository.findByScope("team-a", "agent-a")).thenReturn(Mono.just(persona));
        when(memoryL2Repository.findByScope("team-a", "agent-a")).thenReturn(Flux.just(scene));
        when(pipelineService.process(any(), any())).thenReturn(Mono.just(result(101L)));
        when(conversationService.reducePending(conversation(null, null), 1)).thenReturn(Mono.just(1));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .verifyComplete();

        var candidates = capturedCandidates.get();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().memory().recordId()).isEqualTo("m-1");
        assertThat(candidates.getFirst().candidates())
                .extracting(L1DedupService.ExistingMemory::recordId)
                .containsExactly("m-old", "m-other");

        var requestCaptor = ArgumentCaptor.forClass(L1PipelineService.Request.class);
        verify(pipelineService).process(requestCaptor.capture(), any());
        var state = requestCaptor.getValue().personaState();
        assertThat(state.explicitRequested()).isFalse();
        assertThat(state.personaGenerated()).isTrue();
        assertThat(state.personaBodyPresent()).isTrue();
        assertThat(state.hasScenes()).isTrue();
        assertThat(state.scenesProcessed()).isEqualTo(1);
        assertThat(state.memoriesSinceLastPersona()).isEqualTo(2);
        assertThat(state.threshold()).isEqualTo(10);
    }

    @Test
    void propagatesExtractionFailures() {
        stubConversation(null);
        stubSetting();
        when(messageRepository.listMessages(CONVERSATION_ID, 0L, 10)).thenReturn(Flux.just(message(101L)));
        when(extractionAi.extract(any(), eq(CONVERSATION_ID), anyList()))
                .thenReturn(Mono.error(new IllegalStateException("模型不可用")));

        StepVerifier.create(handler.handle(task(Map.of("conversationId", CONVERSATION_ID))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("模型不可用"))
                .verify();

        verify(conversationService, never()).reducePending(any(), anyInt());
    }

    private void stubConversation(Long l1Cursor) {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Mono.just(conversation(l1Cursor, null)));
    }

    private void stubSetting() {
        stubSetting(10);
    }

    private void stubSetting(int maxMessagesPerExtraction) {
        var spec = new AiSetting().getSpec();
        spec.getMemory().setMaxMessagesPerExtraction(maxMessagesPerExtraction);
        when(settingService.spec()).thenReturn(Mono.just(spec));
    }

    private static AiTask task(Map<String, Object> payload) {
        return new AiTask(
                TASK_ID,
                L1ExtractTaskHandler.TASK_TYPE,
                null,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                CONVERSATION_ID,
                0,
                payload,
                0,
                3,
                NOW,
                NOW,
                NOW);
    }

    private static Conversation conversation(Long l1Cursor, Long l2Cursor) {
        return new Conversation(
                CONVERSATION_ID,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                null,
                "fast",
                "fast-group",
                "ACTIVE",
                l1Cursor,
                l2Cursor,
                2,
                0L,
                NOW,
                NOW,
                NOW);
    }

    private static Message message(long id) {
        return new Message(
                id,
                CONVERSATION_ID,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                null,
                "req-1",
                2,
                0,
                "assistant",
                "内容",
                "TEXT",
                null,
                null,
                NOW);
    }

    private static MemoryContext context() {
        return new MemoryContext("team-a", "user-1", "agent-a", "session-1", null);
    }

    private static ExtractedMemory extracted(String recordId, String content) {
        return new ExtractedMemory(
                recordId, content, MemoryType.PERSONA, 60, "偏好", List.of("101"), Map.of());
    }

    private static L1MemoryRecord record(String recordId, String content, String hash) {
        return L1MemoryRecord.newRecord(
                        recordId,
                        context(),
                        CONVERSATION_ID,
                        content,
                        MemoryType.PERSONA,
                        60,
                        "偏好",
                        List.of(101L),
                        Map.of(),
                        hash,
                        MemoryScope.PRIVATE,
                        false,
                        1,
                        List.of(NOW))
                .withId(70L);
    }

    private static L1PipelineService.Result result(long cursor) {
        return new L1PipelineService.Result(List.of(), List.of(), cursor, 0, List.of());
    }
}
