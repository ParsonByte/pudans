package cn.tolim.pudans.core.ai.harness.team;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class TeamToolsTest {

    private final RuntimeContext context = RuntimeContext.of("user-1", "session-1");

    private final TeamTools tools = new TeamTools(
            new TeamRuntime("team-a", (team, userId) -> Mono.empty()));

    @Test
    void executesTaskAndMailboxActions() throws Exception {
        assertThat(tools.definitions())
                .extracting(tool -> tool.name())
                .containsExactly(TeamTools.TEAM);
        execute(JsonUtils.mapper().createObjectNode()
                .put("action", "addMember")
                .put("member_name", "lead")
                .put("agent_ref", "lead-agent")
                .put("is_lead", true));

        var created = JsonUtils.mapper().readTree(execute(
                JsonUtils.mapper().createObjectNode()
                        .put("action", "createTask")
                        .put("subject", "ship")
                        .put("description", "ship it")
                        .put("owner", "lead")));
        var taskId = created.path("task_id").asText();
        assertThat(created.path("state").asText()).isEqualTo("pending");

        var claimed = JsonUtils.mapper().readTree(execute(
                JsonUtils.mapper().createObjectNode()
                        .put("action", "claimTask")
                        .put("task_id", taskId)
                        .put("member_name", "lead")));
        assertThat(claimed.path("state").asText()).isEqualTo("in_progress");

        var completed = JsonUtils.mapper().readTree(execute(
                JsonUtils.mapper().createObjectNode()
                        .put("action", "completeTask")
                        .put("task_id", taskId)
                        .put("result", "done")));
        assertThat(completed.path("state").asText()).isEqualTo("completed");
        assertThat(completed.path("result").asText()).isEqualTo("done");

        execute(JsonUtils.mapper().createObjectNode()
                .put("action", "addMember")
                .put("member_name", "alice"));
        execute(JsonUtils.mapper().createObjectNode()
                .put("action", "sendMessage")
                .put("member_name", "lead")
                .put("to_member", "alice")
                .put("content", "handoff"));

        JsonNode inbox = JsonUtils.mapper().readTree(execute(
                JsonUtils.mapper().createObjectNode()
                        .put("action", "inbox")
                        .put("member_name", "alice")));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).path("content").asText()).isEqualTo("handoff");
    }

    private String execute(JsonNode arguments) {
        var result = tools.execute(TeamTools.TEAM, arguments, context)
                .block(Duration.ofSeconds(3));
        assertThat(result).isNotNull();
        assertThat(result.error()).as(result.output()).isFalse();
        return result.output();
    }
}