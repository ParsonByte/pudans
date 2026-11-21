package cn.tolim.pudans.core.ai.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class MetaToolFactoryTest {

    @Test
    void buildsAResetToolSchemaFromMetaGroups() {
        var manager = new ToolGroupManager();
        manager.create("analysis", "分析工具", true, ToolGroupScope.META);
        manager.create("admin", "管理工具", false, ToolGroupScope.META);
        manager.create("developer", "开发工具", true, ToolGroupScope.EXTERNAL);

        var tool = new MetaToolFactory(manager).resetToolsTool();

        assertThat(tool.name()).isEqualTo(MetaToolFactory.RESET_TOOLS);
        assertThat(tool.description())
                .contains("analysis")
                .contains("admin")
                .doesNotContain("developer");
        var enumNames = new ArrayList<String>();
        tool.parameters()
                .path("properties")
                .path("to_activate")
                .path("items")
                .path("enum")
                .forEach(node -> enumNames.add(node.asText()));
        assertThat(enumNames).containsExactly("analysis", "admin");
        assertThat(tool.parameters().path("required").get(0).asText()).isEqualTo("to_activate");
    }

    @Test
    void resetsActivationThroughTheRegisteredTool() {
        var toolkit = new HarnessToolkit();
        var manager = toolkit.groupManager();
        manager.create("analysis", "分析工具", true, ToolGroupScope.META);
        manager.create("admin", "管理工具", false, ToolGroupScope.META);
        var factory = new MetaToolFactory(manager);
        toolkit.register(factory.resetToolsTool(), (request, arguments) -> factory.resetTools(arguments));

        var result = toolkit.execute(ToolExecutionRequest.builder()
                .name(MetaToolFactory.RESET_TOOLS)
                .arguments("{\"to_activate\":[\"admin\"]}")
                .build());

        assertThat(result.error()).isFalse();
        assertThat(result.output()).contains("admin");
        assertThat(manager.get("analysis").active()).isFalse();
        assertThat(manager.get("admin").active()).isTrue();
    }

    @Test
    void deactivatesAllMetaGroupsForAnEmptyActivationList() {
        var manager = new ToolGroupManager();
        manager.create("analysis", "分析工具", true, ToolGroupScope.META);
        manager.create("developer", "开发工具", true, ToolGroupScope.EXTERNAL);
        var factory = new MetaToolFactory(manager);

        var result = factory.resetTools(JsonUtils.mapper()
                .createObjectNode()
                .set("to_activate", JsonUtils.mapper().createArrayNode()));

        assertThat(result.error()).isFalse();
        assertThat(result.output()).contains("停用");
        assertThat(manager.get("analysis").active()).isFalse();
        assertThat(manager.get("developer").active()).isTrue();
    }

    @Test
    void reportsInvalidArgumentsAsToolErrors() {
        var factory = new MetaToolFactory(new ToolGroupManager());

        assertThat(factory.resetTools(JsonUtils.mapper().createObjectNode()).error()).isTrue();
        assertThat(factory.resetTools(JsonUtils.mapper()
                        .createObjectNode()
                        .set("to_activate", JsonUtils.mapper().createArrayNode().add(1)))
                .error())
                .isTrue();
    }

    @Test
    void searchesTheActiveToolkitAndReturnsDescriptions() {
        var toolkit = new HarnessToolkit();
        toolkit.register(
                new AiTool(
                        "search_docs",
                        "Search documents",
                        JsonUtils.mapper().createObjectNode().put("type", "object"),
                        null),
                (request, arguments) -> new HarnessToolkit.ToolResult("ok", false));
        toolkit.register(
                new AiTool(
                        "send_mail",
                        "Send mail",
                        JsonUtils.mapper().createObjectNode().put("type", "object"),
                        null),
                (request, arguments) -> new HarnessToolkit.ToolResult("ok", false));
        var factory = new MetaToolFactory(toolkit.groupManager());

        var result = factory.searchTools(
                toolkit, JsonUtils.mapper().createObjectNode().put("query", "documents"));

        assertThat(result.error()).isFalse();
        assertThat(result.output()).contains("search_docs").contains("Search documents");
        assertThat(result.output()).doesNotContain("send_mail");
    }

    @Test
    void readsAndWaitsForAsyncToolTasks() {
        var registry = new InMemoryAsyncToolRegistry();
        var task = registry
                .submit("fast", () -> new HarnessToolkit.ToolResult("done", false))
                .block();
        var factory = new MetaToolFactory(new ToolGroupManager());

        var current = factory.asyncTask(
                        registry,
                        JsonUtils.mapper().createObjectNode().put("task_id", task.id()))
                .block();
        assertThat(current).isNotNull();
        assertThat(current.error()).isFalse();

        var completed = factory.asyncTask(
                        registry,
                        JsonUtils.mapper().createObjectNode()
                                .put("task_id", task.id())
                                .put("wait", true))
                .block();
        assertThat(completed).isNotNull();
        assertThat(completed.error()).isFalse();
        assertThat(completed.output()).contains("\"status\":\"completed\"").contains("\"output\":\"done\"");

        var listed = factory.asyncTask(
                        registry, JsonUtils.mapper().createObjectNode().put("action", "list"))
                .block();
        assertThat(listed).isNotNull();
        assertThat(listed.error()).isFalse();
        assertThat(listed.output()).contains(task.id());
    }

    @Test
    void reportsUnknownGroupsAsToolErrors() {
        var manager = new ToolGroupManager();
        manager.create("analysis", "分析工具", true, ToolGroupScope.META);
        var factory = new MetaToolFactory(manager);

        var result = factory.resetTools(JsonUtils.mapper()
                .createObjectNode()
                .set("to_activate", JsonUtils.mapper().createArrayNode().add("missing")));

        assertThat(result.error()).isTrue();
        assertThat(result.output()).contains("missing");
        assertThat(manager.get("analysis").active()).isTrue();
    }
}