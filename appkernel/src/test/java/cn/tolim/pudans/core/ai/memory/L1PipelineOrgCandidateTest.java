package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.memory.org.OrgCandidateTaskPayload;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class L1PipelineOrgCandidateTest {

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
    void schedulesOrgCandidateTaskAfterStoringACandidate() {
        stubStoredRecord(77L);
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));

        var result = service.process(request(true, false, false, null), storeAll()).block();

        assertThat(result).isNotNull();
        assertThat(result.stored()).hasSize(1);
        assertThat(result.stored().getFirst().orgCandidate()).isTrue();
        assertThat(result.scheduledTasks()).containsExactly(PipelineTaskType.ORG_CANDIDATE);
        var captor = ArgumentCaptor.forClass(MemoryPipelineTask.class);
        verify(taskRepository).insertIfAbsent(captor.capture());
        var task = captor.getValue();
        assertThat(task.taskType()).isEqualTo(PipelineTaskType.ORG_CANDIDATE);
        assertThat(task.dedupKey()).isEqualTo("ORG:team-1:agent-1");
        assertThat(task.payload()).containsEntry(OrgCandidateTaskPayload.AFTER_L1_ID, 77L);
        assertThat(task.schedule().conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(task.schedule().priority()).isZero();
        assertThat(task.schedule().nextRunAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotScheduleOrgCandidateWithoutAStoredCandidate() {
        stubStoredRecord(77L);

        var result = service.process(request(false, false, false, null), storeAll()).block();

        assertThat(result).isNotNull();
        assertThat(result.stored()).hasSize(1);
        assertThat(result.stored().getFirst().orgCandidate()).isFalse();
        assertThat(result.scheduledTasks()).isEmpty();
        verify(taskRepository, never()).insertIfAbsent(any());
    }

    @Test
    void schedulesOrgCandidateAlongsideL2AndL3() {
        stubStoredRecord(77L);
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));
        var personaState = new PersonaTrigger.State(false, true, true, true, 1, 9, 10);

        var result = service.process(request(true, true, true, personaState), storeAll()).block();

        assertThat(result).isNotNull();
        assertThat(result.scheduledTasks())
                .containsExactly(
                        PipelineTaskType.L2_SCENE,
                        PipelineTaskType.L3_PERSONA,
                        PipelineTaskType.ORG_CANDIDATE);
        verify(taskRepository, times(3)).insertIfAbsent(any());
    }

    private void stubStoredRecord(long id) {
        when(l1Repository.insert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L1MemoryRecord>getArgument(0).withId(id)));
        when(conversationRepository.advanceL1Cursor(CONVERSATION_ID, 101L)).thenReturn(Mono.just(true));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());
    }

    private static L1Pipeline.ConflictJudge storeAll() {
        return (prompt, candidates) -> candidates.stream()
                .map(candidate -> DedupDecision.store(candidate.memory().recordId()))
                .toList();
    }

    private static L1Pipeline.Request request(
            boolean orgCandidate, boolean l2Enabled, boolean l3Enabled, PersonaTrigger.State personaState) {
        return new L1Pipeline.Request(
                context(),
                CONVERSATION_ID,
                List.of(scene("release", extracted("m-new", "new content"))),
                List.of(),
                101L,
                MemoryScope.PRIVATE,
                orgCandidate,
                l2Enabled,
                l3Enabled,
                personaState,
                MemoryPromptInput.none(),
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
                recordId, content, MemoryType.WORK_METHOD, 80, "release", List.of("101"), Map.of());
    }
}
