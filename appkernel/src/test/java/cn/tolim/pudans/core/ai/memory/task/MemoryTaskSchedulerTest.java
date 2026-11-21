package cn.tolim.pudans.core.ai.memory.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.MemoryPipelineTask;
import cn.tolim.pudans.core.ai.memory.PipelineTaskType;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryTaskRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryTaskSchedulerTest {

    @Mock
    AiMemoryTaskRepository taskRepository;

    @Mock
    AiSettings aiSettings;

    @InjectMocks
    MemoryTaskScheduler scheduler;

    @Test
    void enqueuesOneL1TaskPerConversation() {
        when(aiSettings.memoryEnabled()).thenReturn(Mono.just(true));
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));

        StepVerifier.create(scheduler.enqueueExtraction(conversation(), "user-1", 21L))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(MemoryPipelineTask.class);
        verify(taskRepository).insertIfAbsent(captor.capture());
        var task = captor.getValue();
        assertThat(task.taskType()).isEqualTo(PipelineTaskType.L1_EXTRACT);
        assertThat(task.dedupKey()).isEqualTo("l1:7");
        assertThat(task.context().teamId()).isEqualTo("team-a");
        assertThat(task.context().userId()).isEqualTo("user-1");
        assertThat(task.context().agentId()).isEqualTo("agent-a");
        assertThat(task.context().sessionId()).isEqualTo("session-1");
        assertThat(task.context().taskId()).isNull();
        assertThat(task.payload())
                .containsEntry("conversationId", 7L)
                .containsEntry("lastMessageId", 21L);
        assertThat(task.schedule().conversationId()).isEqualTo(7L);
        assertThat(task.schedule().priority()).isZero();
    }

    @Test
    void enqueuesL2RebuildTaskWithScopeAndCursor() {
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));

        StepVerifier.create(scheduler.enqueueL2("team-a", "agent-a", 7L, "admin-1", 42L))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(MemoryPipelineTask.class);
        verify(taskRepository).insertIfAbsent(captor.capture());
        var task = captor.getValue();
        assertThat(task.taskType()).isEqualTo(PipelineTaskType.L2_SCENE);
        assertThat(task.dedupKey()).isEqualTo("L2:team-a:agent-a");
        assertThat(task.context().teamId()).isEqualTo("team-a");
        assertThat(task.context().userId()).isEqualTo("admin-1");
        assertThat(task.context().agentId()).isEqualTo("agent-a");
        assertThat(task.context().sessionId()).isEqualTo("rebuild");
        assertThat(task.payload())
                .containsEntry("teamId", "team-a")
                .containsEntry("agentId", "agent-a")
                .containsEntry("afterL1Id", 42L);
        assertThat(task.schedule().conversationId()).isEqualTo(7L);
        assertThat(task.schedule().priority()).isZero();
        assertThat(task.schedule().nextRunAt()).isNotNull();
    }

    @Test
    void enqueuesL3RebuildTaskWithScopeAndCursor() {
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));

        StepVerifier.create(scheduler.enqueueL3("team-a", "agent-a", 7L, "admin-1", 42L))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(MemoryPipelineTask.class);
        verify(taskRepository).insertIfAbsent(captor.capture());
        var task = captor.getValue();
        assertThat(task.taskType()).isEqualTo(PipelineTaskType.L3_PERSONA);
        assertThat(task.dedupKey()).isEqualTo("L3:team-a:agent-a");
        assertThat(task.context().userId()).isEqualTo("admin-1");
        assertThat(task.context().sessionId()).isEqualTo("rebuild");
        assertThat(task.payload())
                .containsEntry("teamId", "team-a")
                .containsEntry("agentId", "agent-a")
                .containsEntry("afterL1Id", 42L);
        assertThat(task.schedule().conversationId()).isEqualTo(7L);
        assertThat(task.schedule().priority()).isZero();
    }

    @Test
    void swallowsEnqueueFailures() {
        when(aiSettings.memoryEnabled()).thenReturn(Mono.just(true));
        when(taskRepository.insertIfAbsent(any()))
                .thenReturn(Mono.error(new IllegalStateException("队列不可用")));

        StepVerifier.create(scheduler.enqueueExtraction(conversation(), "user-1", 21L))
                .verifyComplete();
    }

    @Test
    void skipsExtractionWhenMemoryIsDisabled() {
        when(aiSettings.memoryEnabled()).thenReturn(Mono.just(false));

        StepVerifier.create(scheduler.enqueueExtraction(conversation(), "user-1", 21L))
                .verifyComplete();

        verify(taskRepository, org.mockito.Mockito.never()).insertIfAbsent(any());
    }

    @Test
    void importExtractionIgnoresGlobalSwitch() {
        when(taskRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));

        StepVerifier.create(scheduler.enqueueExtractionForImport(conversation(), "user-1", 21L))
                .verifyComplete();

        verify(taskRepository).insertIfAbsent(any());
    }

    private static Conversation conversation() {
        return new Conversation(
                7L,
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
                null,
                null,
                0,
                0L,
                Instant.parse("2026-11-19T00:00:00Z"),
                Instant.parse("2026-11-19T00:00:00Z"),
                Instant.parse("2026-11-19T00:00:00Z"));
    }
}
