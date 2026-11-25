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
    void declaresTheRealToolSchema() {
        var tool = new GenerateMediaTool(tasks);

        assertThat(tool.specification().name()).isEqualTo(GenerateMediaTool.NAME);
        assertThat(tool.specification().parameters().required())
                .containsExactly("media_type", "model_id", "prompt");
    }

    @Test
    void createsARealTaskWithTheAgentRunIdentity() {
        var tool = new GenerateMediaTool(tasks);
        var runId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var quotaId = UUID.randomUUID();
        var modelId = UUID.randomUUID();
        var context = new AgentToolContext(runId, sessionId, "alice", "workspace-a", quotaId);
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

        StepVerifier.create(tool.execute(
                        context,
                        Map.of(
                                "media_type", "image",
                                "model_id", modelId.toString(),
                                "prompt", "a prompt",
                                "settings", Map.of("size", "1024x1024"),
                                "input_asset_ids", List.of())))
                .assertNext(result -> assertThat(result)
                        .containsEntry("taskId", summary.id())
                        .containsEntry("status", "queued"))
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

        StepVerifier.create(tool.execute(
                        null,
                        Map.of(
                                "media_type", "image",
                                "model_id", UUID.randomUUID().toString(),
                                "prompt", "prompt")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("agent-tool-context-missing")))
                .verify();
    }
}