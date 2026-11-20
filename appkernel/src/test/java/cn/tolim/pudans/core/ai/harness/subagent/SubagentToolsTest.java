package cn.tolim.pudans.core.ai.harness.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class SubagentToolsTest {

    private final RuntimeContext context = RuntimeContext.of("user-1", "session-1");

    private final SubagentRuntime runtime = new SubagentRuntime()
            .register("helper", parent -> new EchoAgentFixture("helper", null));

    private final SubagentTools tools = new SubagentTools(runtime);

    @Test
    void exposesAndExecutesSpawnSendListRelease() throws Exception {
        assertThat(tools.definitions())
                .extracting(tool -> tool.name())
                .containsExactlyInAnyOrder(
                        SubagentTools.AGENT_SPAWN,
                        SubagentTools.AGENT_SEND,
                        SubagentTools.AGENT_LIST,
                        SubagentTools.AGENT_RELEASE,
                        SubagentTools.AGENT_TASK);

        var spawnArgs = JsonUtils.mapper().createObjectNode()
                .put("agent_id", "helper")
                .put("task", "hello")
                .put("reusable", true)
                .put("label", "tool-label");
        var spawned = tools.execute(SubagentTools.AGENT_SPAWN, spawnArgs, context)
                .block(Duration.ofSeconds(3));

        assertThat(spawned).isNotNull();
        assertThat(spawned.error()).isFalse();
        var spawnedJson = JsonUtils.mapper().readTree(spawned.output());
        assertThat(spawnedJson.path("state").asText()).isEqualTo("completed");
        assertThat(spawnedJson.path("result").asText()).isEqualTo("helper:hello");

        var sendArgs = JsonUtils.mapper().createObjectNode()
                .put("agent_key", spawnedJson.path("agent_key").asText())
                .put("message", "follow up");
        var sent = tools.execute(SubagentTools.AGENT_SEND, sendArgs, context)
                .block(Duration.ofSeconds(3));

        assertThat(sent).isNotNull();
        assertThat(sent.error()).isFalse();
        assertThat(JsonUtils.mapper().readTree(sent.output()).path("result").asText())
                .isEqualTo("helper:follow up");

        var listed = tools.execute(SubagentTools.AGENT_LIST, null, context)
                .block(Duration.ofSeconds(3));
        JsonNode list = JsonUtils.mapper().readTree(listed.output());
        assertThat(list.isArray()).isTrue();
        assertThat(list).hasSize(1);

        var releaseArgs = JsonUtils.mapper().createObjectNode()
                .put("agent_key", spawnedJson.path("agent_key").asText());
        var released = tools.execute(SubagentTools.AGENT_RELEASE, releaseArgs, context)
                .block(Duration.ofSeconds(3));

        assertThat(released).isNotNull();
        assertThat(released.error()).isFalse();
        assertThat(released.output()).contains("released");
    }

    @Test
    void readsAndAwaitsBackgroundTasks() {
        var gate = Sinks.<String>one();
        var runtime = new SubagentRuntime()
                .register("helper", parent -> new EchoAgentFixture("helper", gate));
        var tools = new SubagentTools(runtime);
        var spawnArgs = JsonUtils.mapper().createObjectNode()
                .put("agent_id", "helper")
                .put("task", "later")
                .put("timeout_seconds", 0);
        var spawned = tools.execute(SubagentTools.AGENT_SPAWN, spawnArgs, context)
                .block(Duration.ofSeconds(3));
        var taskId = json(spawned.output()).path("task_id").asText();
        assertThat(taskId).isNotBlank();

        var current = tools.execute(
                        SubagentTools.AGENT_TASK,
                        JsonUtils.mapper().createObjectNode().put("task_id", taskId),
                        context)
                .block(Duration.ofSeconds(3));
        assertThat(current).isNotNull();
        assertThat(current.error()).isFalse();
        assertThat(json(current.output()).path("state").asText())
                .isEqualTo("running");

        StepVerifier.create(tools.execute(
                        SubagentTools.AGENT_TASK,
                        JsonUtils.mapper().createObjectNode()
                                .put("task_id", taskId)
                                .put("wait", true),
                        context))
                .then(() -> gate.tryEmitValue("done"))
                .assertNext(result -> {
                    assertThat(result.error()).isFalse();
                    var task = json(result.output());
                    assertThat(task.path("state").asText()).isEqualTo("completed");
                    assertThat(task.path("result").asText()).isEqualTo("helper:done");
                })
                .verifyComplete();
    }

    @Test
    void reportsInvalidArgumentsAsToolErrors() {
        var result = tools.execute(
                        SubagentTools.AGENT_SPAWN,
                        JsonUtils.mapper().createObjectNode(),
                        context)
                .block(Duration.ofSeconds(3));

        assertThat(result).isNotNull();
        assertThat(result.error()).isTrue();
        assertThat(result.output()).contains("agent_id");
    }

    private static JsonNode json(String value) {
        try {
            return JsonUtils.mapper().readTree(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}