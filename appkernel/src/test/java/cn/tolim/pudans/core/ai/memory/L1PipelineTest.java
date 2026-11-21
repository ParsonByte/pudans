package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class L1PipelineTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final long CONVERSATION_ID = 9L;

    @Mock
    AiMemoryL1Repository l1Repository;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMemoryTaskRepository taskRepository;

    @Mock
    AiMemoryGenerationLogRepository generationLogRepository;

    private L1Pipeline service;

    @BeforeEach
    void setUp() {
        service = new L1Pipeline(
                new L1Deduplication(),
                new MemoryPromptResolver(),
                l1Repository,
                conversationRepository,
                taskRepository,
                generationLogRepository,
                new PersonaTrigger(),
                null);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesNewMemoryAdvancesCursorAndSchedulesL2() {
        when(l1Repository.insert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(77L)));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(List.of(), true, false, null),
                        (prompt, candidates) -> candidates.stream()
                                .map(candidate -> DedupDecision.store(candidate.memory().recordId()))
                                .toList())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.stored()).hasSize(1);
        var stored = result.stored().getFirst();
        assertThat(stored.id()).isEqualTo(77L);
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.status()).isEqualTo(MemoryStatus.ACTIVE);
        assertThat(stored.context()).isEqualTo(context());
        assertThat(stored.timestamps()).containsExactly(NOW);
        assertThat(stored.dedupHash()).hasSize(64);
        assertThat(result.l1Cursor()).isEqualTo(101L);
        assertThat(result.scheduledTasks()).containsExactly(PipelineTaskType.L2_SCENE);
        verify(generationLogRepository).insert(any(MemoryGenerationLog.class));
        verify(taskRepository).insertIfAbsent(any(MemoryPipelineTask.class));
    }

    @Test
    void skipDecisionWritesNothingButStillAdvancesCursor() {
        var candidate = candidate("m-old", "old content", 2);
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));

        var result = service.process(
                        request(List.of(candidate), true, false, null),
                        (prompt, candidates) -> List.of(new DedupDecision(
                                "m-new", DedupAction.SKIP, List.of(), null, null, null, List.of())))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.stored()).isEmpty();
        assertThat(result.l1Cursor()).isEqualTo(101L);
        assertThat(result.scheduledTasks()).isEmpty();
        verify(l1Repository, never()).insert(any());
        verify(generationLogRepository, never()).insert(any());
        verify(taskRepository, never()).insertIfAbsent(any());
    }

    @Test
    void updateSupersedesTargetAndIncrementsVersion() {
        var candidate = candidate("m-old", "old content", 2);
        var mergedTimestamp = "2026-01-01T00:00:00Z";
        when(l1Repository.insert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(88L)));
        when(l1Repository.supersede(List.of("m-old"), 88L)).thenReturn(Mono.just(1L));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(List.of(candidate), false, false, null),
                        (prompt, candidates) -> List.of(new DedupDecision(
                                "m-new",
                                DedupAction.UPDATE,
                                List.of("m-old"),
                                "merged content",
                                MemoryType.INSTRUCTION,
                                -1,
                                List.of(mergedTimestamp))))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.stored()).hasSize(1);
        var stored = result.stored().getFirst();
        assertThat(stored.content()).isEqualTo("merged content");
        assertThat(stored.type()).isEqualTo(MemoryType.INSTRUCTION);
        assertThat(stored.priority()).isEqualTo(-1);
        assertThat(stored.version()).isEqualTo(3);
        assertThat(stored.timestamps()).containsExactly(Instant.parse(mergedTimestamp));
        assertThat(result.supersededCount()).isEqualTo(1L);
        verify(l1Repository).supersede(List.of("m-old"), 88L);
    }

    @Test
    void updatesExistingRowInPlaceWhenMergedHashMatchesTarget() {
        var dedupService = new L1Deduplication();
        var content = "Same content";
        var candidate = L1MemoryRecord.newRecord(
                        "m-old",
                        context(),
                        CONVERSATION_ID,
                        content,
                        MemoryType.EPISODIC,
                        50,
                        "release",
                        List.of(101L),
                        Map.of(),
                        dedupService.dedupHash(context(), content),
                        MemoryScope.PRIVATE,
                        false,
                        4,
                        List.of(NOW))
                .withId(7L);
        when(l1Repository.updateExisting(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(55L)));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(List.of(candidate), false, false, null),
                        (prompt, candidates) -> List.of(new DedupDecision(
                                "m-new",
                                DedupAction.UPDATE,
                                List.of("m-old"),
                                content,
                                null,
                                null,
                                List.of())))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.stored()).hasSize(1);
        var updated = result.stored().getFirst();
        assertThat(updated.id()).isEqualTo(55L);
        assertThat(updated.recordId()).isEqualTo("m-old");
        assertThat(updated.version()).isEqualTo(5);
        assertThat(updated.content()).isEqualTo(content);
        assertThat(result.supersededCount()).isZero();
        verify(l1Repository, never()).insert(any());
        verify(l1Repository, never()).supersede(any(), anyLong());
    }

    @Test
    void schedulesL3WhenPersonaTriggerFires() {
        var personaState = new PersonaTrigger.State(false, true, true, true, 1, 1, 10);
        when(l1Repository.insert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(77L)));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(List.of(), true, true, personaState),
                        (prompt, candidates) -> candidates.stream()
                                .map(candidate -> DedupDecision.store(candidate.memory().recordId()))
                                .toList())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.scheduledTasks())
                .containsExactly(PipelineTaskType.L2_SCENE, PipelineTaskType.L3_PERSONA);
        verify(taskRepository, times(2)).insertIfAbsent(any(MemoryPipelineTask.class));
    }

    @Test
    void schedulesL3WhenBatchCrossesMemoryThreshold() {
        var personaState = new PersonaTrigger.State(false, true, true, true, 3, 9, 10);
        when(l1Repository.insert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(77L)));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(List.of(), false, true, personaState),
                        (prompt, candidates) -> candidates.stream()
                                .map(candidate -> DedupDecision.store(candidate.memory().recordId()))
                                .toList())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.scheduledTasks()).containsExactly(PipelineTaskType.L3_PERSONA);
    }

    @Test
    void passesComposedPromptToJudgeAndGenerationLog() {
        var promptReference = new AtomicReference<String>();
        var resolved = new ResolvedMemoryPrompt(
                AiMemoryPrompt.Layer.L1, MemoryPromptSource.TEAM, "team-l1", 2, "关注架构决策");
        when(l1Repository.insert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(77L)));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        service.process(
                        request(
                                List.of(candidate("m-old", "old content", 2)),
                                true,
                                false,
                                null,
                                new MemoryPromptInput("BASE_PROTOCOL", resolved)),
                        (prompt, candidates) -> {
                            promptReference.set(prompt);
                            return candidates.stream()
                                    .map(candidate -> DedupDecision.store(candidate.memory().recordId()))
                                    .toList();
                        })
                .block();

        var composed = promptReference.get();
        assertThat(composed).startsWith("BASE_PROTOCOL").contains("关注架构决策");
        var logCaptor = ArgumentCaptor.forClass(MemoryGenerationLog.class);
        verify(generationLogRepository).insert(logCaptor.capture());
        var log = logCaptor.getValue();
        assertThat(log.memoryLevel()).isEqualTo(AiMemoryPrompt.Layer.L1);
        assertThat(log.promptId()).isEqualTo("team-l1");
        assertThat(log.promptVersion()).isEqualTo(2);
        assertThat(log.promptSource()).isEqualTo(MemoryPromptSource.TEAM);
        assertThat(log.promptHash()).isEqualTo(MemoryHashes.sha256Hex(composed));
    }

    private static L1Pipeline.Request request(
            List<L1MemoryRecord> candidates,
            boolean l2Enabled,
            boolean l3Enabled,
            PersonaTrigger.State personaState) {
        return request(candidates, l2Enabled, l3Enabled, personaState, new MemoryPromptInput("BASE_PROTOCOL", null));
    }

    private static L1Pipeline.Request request(
            List<L1MemoryRecord> candidates,
            boolean l2Enabled,
            boolean l3Enabled,
            PersonaTrigger.State personaState,
            MemoryPromptInput prompt) {
        return new L1Pipeline.Request(
                context(),
                CONVERSATION_ID,
                List.of(scene("release", extracted("m-new", "new content"))),
                candidates,
                101L,
                MemoryScope.PRIVATE,
                false,
                l2Enabled,
                l3Enabled,
                personaState,
                prompt,
                MemoryGenerationInput.empty());
    }

    private static MemoryContext context() {
        return new MemoryContext("team-1", "user-1", "agent-1", "session-1", null);
    }

    private static SceneSegment scene(String sceneName, ExtractedMemory... memories) {
        return new SceneSegment(sceneName, List.of("101"), List.of(memories));
    }

    private static ExtractedMemory extracted(String recordId, String content) {
        return new ExtractedMemory(
                recordId, content, MemoryType.EPISODIC, 50, "release", List.of("101"), Map.of());
    }

    private static L1MemoryRecord candidate(String recordId, String content, int version) {
        return L1MemoryRecord.newRecord(
                        recordId,
                        context(),
                        CONVERSATION_ID,
                        content,
                        MemoryType.EPISODIC,
                        50,
                        "release",
                        List.of(101L),
                        Map.of(),
                        "hash-" + recordId,
                        MemoryScope.PRIVATE,
                        false,
                        version,
                        List.of(NOW))
                .withId(7L);
    }
}
