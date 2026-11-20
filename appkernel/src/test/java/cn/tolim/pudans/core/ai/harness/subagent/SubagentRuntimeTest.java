package cn.tolim.pudans.core.ai.harness.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.agent.AgentRunStatus;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class SubagentRuntimeTest {

    private static final RuntimeContext PARENT = RuntimeContext.of("user-1", "session-1");

    @Test
    void spawnsSynchronouslyAndSendsFollowUp() {
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var runtime = new SubagentRuntime((key, event) -> events.add(event))
                .register("helper", parent -> new EchoAgentFixture("helper", null));
        var spawn = SubagentSpawnRequest.builder()
                .agentId("helper")
                .task("hello")
                .reusable(true)
                .label("h1")
                .build();

        var first = runtime.spawn(spawn, PARENT).block(Duration.ofSeconds(3));

        assertThat(first).isNotNull();
        assertThat(first.state()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(first.result()).isEqualTo("helper:hello");
        assertThat(first.agentKey()).startsWith("agent:helper:");
        assertThat(first.taskId()).isNotBlank();

        var second = runtime.send(SubagentSendRequest.builder()
                        .agentKey(first.agentKey())
                        .message("again")
                        .build(), PARENT)
                .block(Duration.ofSeconds(3));

        assertThat(second).isNotNull();
        assertThat(second.result()).isEqualTo("helper:again");
        assertThat(runtime.list(PARENT).collectList().block(Duration.ofSeconds(3)))
                .singleElement()
                .satisfies(handle -> {
                    assertThat(handle.label()).isEqualTo("h1");
                    assertThat(handle.reusable()).isTrue();
                });
        assertThat(events).anyMatch(event -> event instanceof AgentEvent.AgentStart);
        assertThat(events).anyMatch(event -> event instanceof AgentEvent.AgentResult);
        assertThat(events).anyMatch(event -> event instanceof AgentEvent.AgentEnd);
    }

    @Test
    void completesBackgroundSpawnLater() {
        var gate = Sinks.<String>one();
        var runtime = new SubagentRuntime()
                .register("helper", parent -> new EchoAgentFixture("helper", gate));
        var spawn = SubagentSpawnRequest.builder()
                .agentId("helper")
                .task("later")
                .mode(SubagentMode.BACKGROUND)
                .build();

        var accepted = runtime.spawn(spawn, PARENT).block(Duration.ofSeconds(3));

        assertThat(accepted).isNotNull();
        assertThat(accepted.background()).isTrue();
        assertThat(accepted.taskId()).isNotBlank();
        assertThat(accepted.terminal()).isFalse();

        StepVerifier.create(runtime.awaitTask(accepted.taskId(), PARENT))
                .then(() -> gate.tryEmitValue("done"))
                .assertNext(task -> {
                    assertThat(task.state()).isEqualTo(AgentRunStatus.COMPLETED);
                    assertThat(task.result()).isEqualTo("helper:done");
                })
                .verifyComplete();
        assertThat(runtime.list(PARENT).collectList().block(Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void persistsChildSessionByParentRunAndReleasesIdleHandles() {
        var contexts = new CopyOnWriteArrayList<RuntimeContext>();
        var runtime = new SubagentRuntime().register("helper", true, parent -> {
            contexts.add(parent);
            return new EchoAgentFixture("helper", null);
        });
        var request = SubagentSpawnRequest.builder()
                .agentId("helper")
                .task("first")
                .reusable(true)
                .label("persisted")
                .parentRunId("run-1")
                .build();

        var first = runtime.spawn(request, PARENT).block(Duration.ofSeconds(3));
        var second = runtime.spawn(persistentRequest("second", "run-1", "persisted"), PARENT)
                .block(Duration.ofSeconds(3));

        assertThat(second).isNotNull();
        assertThat(second.agentKey()).isEqualTo(first.agentKey());
        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.result()).isEqualTo("helper:second");
        assertThat(contexts).hasSize(1);

        var otherRun = runtime.spawn(persistentRequest("other", "run-2", "persisted-other"), PARENT)
                .block(Duration.ofSeconds(3));
        assertThat(otherRun.sessionId()).isNotEqualTo(first.sessionId());

        runtime.release(first.agentKey(), null, PARENT).block(Duration.ofSeconds(3));
        StepVerifier.create(runtime.send(SubagentSendRequest.builder()
                        .agentKey(first.agentKey())
                        .message("after release")
                        .build(), PARENT))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void rejectsReleaseWhileBackgroundWorkRuns() {
        var gate = Sinks.<String>one();
        var runtime = new SubagentRuntime()
                .register("helper", parent -> new EchoAgentFixture("helper", gate));
        var spawn = SubagentSpawnRequest.builder()
                .agentId("helper")
                .task("busy")
                .reusable(true)
                .mode(SubagentMode.BACKGROUND)
                .build();
        var accepted = runtime.spawn(spawn, PARENT).block(Duration.ofSeconds(3));

        StepVerifier.create(runtime.release(accepted.agentKey(), null, PARENT))
                .expectError(IllegalStateException.class)
                .verify();

        gate.tryEmitValue("done");
        runtime.awaitTask(accepted.taskId(), PARENT).block(Duration.ofSeconds(3));
        runtime.release(accepted.agentKey(), null, PARENT).block(Duration.ofSeconds(3));
        assertThat(runtime.list(PARENT).collectList().block(Duration.ofSeconds(3))).isEmpty();
    }

    private static SubagentSpawnRequest persistentRequest(
            String task, String parentRunId, String label) {
        return SubagentSpawnRequest.builder()
                .agentId("helper")
                .task(task)
                .reusable(true)
                .label(label)
                .parentRunId(parentRunId)
                .build();
    }

    }