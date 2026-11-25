package cn.tolim.pudans.core.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskStartRequest;
import cn.tolim.pudans.contract.creation.dto.GenerationTaskSummary;
import cn.tolim.pudans.core.creation.task.GenerationTasks;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import io.agentscope.core.agent.RuntimeContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GenerateMediaToolTest {

    @Mock
    GenerationTasks tasks;

    @Test
    void createsARealTaskWithTheAgentRunIdentity() {
        var tool = new GenerateMediaTool(tasks);
        var runId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var quotaId = UUID.randomUUID();
        var modelId = UUID.randomUUID();
        var context = RuntimeContext.builder()
                .put(AgentToolContext.class, new AgentToolContext(
                        runId, sessionId, "alice", "workspace-a", quotaId))
                .build();
        var summary = new GenerationTaskSummary(
                UUID.randomUUID(),
                "image",
                "text",
                modelId,
                "a prompt",
                GenerationTask.Status.QUEUED,
                false,
                List.of(),
                null,
                null,
                Instant.now(),
                null,
                null);
        when(tasks.start(any(), any(), anyString()))
                .thenReturn(Mono.just(new GenerationTasks.StartResult(summary, true)));

        StepVerifier.create(tool.generateMedia(
                        "image",
                        null,
                        modelId.toString(),
                        "a prompt",
                        Map.of("size", "1024x1024"),
                        List.of(),
                        context))
                .assertNext(result -> {
                    assertThat(result)
                            .containsEntry("taskId", summary.id())
                            .containsEntry("status", "queued");
                })
                .verifyComplete();

        var identity = ArgumentCaptor.forClass(RequestIdentity.class);
        var request = ArgumentCaptor.forClass(GenerationTaskStartRequest.class);
        verify(tasks).start(identity.capture(), request.capture(), anyString());
        assertThat(identity.getValue())
                .isEqualTo(new RequestIdentity("alice", "workspace-a"));
        assertThat(request.getValue())
                .satisfies(value -> {
                    assertThat(value.operation()).isEqualTo("text");
                    assertThat(value.quotaReservationId()).isEqualTo(quotaId);
                    assertThat(value.runId()).isEqualTo(runId);
                    assertThat(value.sessionId()).isEqualTo(sessionId);
                });
    }

    @Test
    void rejectsMissingRuntimeToolContext() {
        var tool = new GenerateMediaTool(tasks);

        StepVerifier.create(tool.generateMedia(
                        "image",
                        "text",
                        UUID.randomUUID().toString(),
                        "prompt",
                        Map.of(),
                        List.of(),
                        RuntimeContext.empty()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("agent-tool-context-missing")))
                .verify();
    }
}
