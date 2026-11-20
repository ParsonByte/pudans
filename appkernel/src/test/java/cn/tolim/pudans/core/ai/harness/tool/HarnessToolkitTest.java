package cn.tolim.pudans.core.ai.harness.tool;

import static cn.tolim.pudans.core.ai.harness.tool.AsyncToolTestSupport.awaitTerminal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarnessToolkitTest {

    @Test
    void registersAnnotatedMethodsAndExecutesThem() {
        var toolkit = new HarnessToolkit().register(new EchoTools());

        assertThat(toolkit.executors()).hasSize(1);
        var executor = toolkit.executors().values().iterator().next();
        var request = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();

        assertThat(executor.execute(request, null)).isEqualTo("echo:hello");
    }

    @Test
    void registersDynamicToolsWithAHandler() {
        var tool = new AiTool(
                "lookup",
                "Look up a value",
                JsonUtils.mapper().createObjectNode().put("type", "object"),
                null);
        var toolkit = new HarnessToolkit()
                .register(tool, (request, arguments) -> new HarnessToolkit.ToolResult(
                        "value:" + arguments.path("key").asText(), false));
        var request = ToolExecutionRequest.builder()
                .name("lookup")
                .arguments("{\"key\":\"alpha\"}")
                .build();

        assertThat(toolkit.executors().values().iterator().next().execute(request, null))
                .isEqualTo("value:alpha");
    }

    @Test
    void routesUndeclaredToolCallsToTheFallbackHandler() {
        var toolkit = new HarnessToolkit().fallbackHandler((request, arguments) -> new HarnessToolkit.ToolResult(
                request.name() + ":" + arguments.path("key").asText(), false));
        var request = ToolExecutionRequest.builder()
                .name("skill")
                .arguments("{\"key\":\"alpha\"}")
                .build();

        var result = toolkit.execute(request);

        assertThat(result.error()).isFalse();
        assertThat(result.output()).isEqualTo("skill:alpha");
    }

    @Test
    void rejectsDuplicateToolNames() {
        var tool = new AiTool(
                "echo",
                "Echo",
                JsonUtils.mapper().createObjectNode().put("type", "object"),
                null);
        var toolkit = new HarnessToolkit().register(new EchoTools());

        assertThatThrownBy(() -> toolkit.register(
                        tool, (request, arguments) -> new HarnessToolkit.ToolResult("", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("echo");
    }

    @Test
    void registersToolsIntoGroupsAndFiltersInactiveSpecifications() {
        var toolkit = new HarnessToolkit();
        toolkit.groupManager().create("analysis", "分析工具", false, ToolGroupScope.META);
        toolkit.register(new EchoTools(), "analysis");

        assertThat(toolkit.specifications()).extracting(ToolSpecification::name).containsExactly("echo");
        assertThat(toolkit.activeSpecifications()).isEmpty();

        var request = ToolExecutionRequest.builder()
                .name("echo")
                .arguments("{\"text\":\"hello\"}")
                .build();
        var blocked = toolkit.execute(request);

        assertThat(blocked.error()).isTrue();
        assertThat(blocked.output()).contains("echo");

        toolkit.groupManager().setActive("analysis", true);

        assertThat(toolkit.activeSpecifications()).extracting(ToolSpecification::name).containsExactly("echo");
        assertThat(toolkit.execute(request).output()).isEqualTo("echo:hello");
    }

    @Test
    void rejectsRegistrationIntoAnUnknownGroup() {
        var tool = new AiTool(
                "lookup",
                "Look up a value",
                JsonUtils.mapper().createObjectNode().put("type", "object"),
                null);
        var toolkit = new HarnessToolkit();

        assertThatThrownBy(() -> toolkit.register(
                        tool,
                        (request, arguments) -> new HarnessToolkit.ToolResult("", false),
                        "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        assertThat(toolkit.specifications()).isEmpty();
    }

    @Test
    void searchesActiveSpecificationsByNameAndDescription() {
        var toolkit = new HarnessToolkit();
        toolkit.groupManager().create("archive", "归档工具", false, ToolGroupScope.META);
        toolkit.register(new SearchTools());
        toolkit.register(new ArchivedTools(), "archive");

        assertThat(toolkit.search("weather", 5).toolNames()).containsExactly("fetch_weather");
        assertThat(toolkit.search("天气", 5).toolNames()).containsExactly("fetch_weather");
        assertThat(toolkit.search("archive", 5).toolNames()).isEmpty();

        toolkit.groupManager().setActive("archive", true);

        assertThat(toolkit.search("archive", 5).toolNames()).containsExactly("archive_report");
    }

    @Test
    void usesTheConfiguredSearchStrategy() {
        var toolkit = new HarnessToolkit(request -> new ToolSearchResult(List.of()))
                .register(new EchoTools());

        assertThat(toolkit.search("echo", 5).toolNames()).isEmpty();
    }

    @Test
    void submitsAsyncToolsAndReadsTheParsedArguments() {
        var registry = new InMemoryAsyncToolRegistry();
        var toolkit = new HarnessToolkit().asyncRegistry(registry);
        var tool = new AiTool(
                "slow_lookup",
                "Look up a value",
                JsonUtils.mapper().createObjectNode().put("type", "object"),
                null);
        toolkit.registerAsync(tool, (request, arguments) -> new HarnessToolkit.ToolResult(
                "value:" + arguments.path("key").asText(), false));

        var started = toolkit.execute(ToolExecutionRequest.builder()
                .name("slow_lookup")
                .arguments("{\"key\":\"alpha\"}")
                .build());

        assertThat(started.error()).isFalse();
        assertThat(started.output()).contains("async-tool-");
        var taskId = registry.list().block().getFirst().id();
        var completed = awaitTerminal(registry, taskId);
        assertThat(completed.status()).isEqualTo(AsyncToolStatus.COMPLETED);
        assertThat(completed.output()).isEqualTo("value:alpha");
    }

    @Test
    void rejectsAsyncExecutionWithoutARegistry() {
        var toolkit = new HarnessToolkit();
        var tool = new AiTool(
                "slow_lookup",
                "Look up a value",
                JsonUtils.mapper().createObjectNode().put("type", "object"),
                null);
        toolkit.registerAsync(tool, (request, arguments) -> new HarnessToolkit.ToolResult("unused", false));

        var result = toolkit.execute(ToolExecutionRequest.builder()
                .name("slow_lookup")
                .arguments("{}")
                .build());

        assertThat(result.error()).isTrue();
        assertThat(result.output()).contains("异步工具注册表未配置");
    }

    private static final class EchoTools {

        @Tool(name = "echo", value = "Echo text")
        String echo(@P("text") String text) {
            return "echo:" + text;
        }
    }

    private static final class SearchTools {

        @Tool(name = "fetch_weather", value = "查询指定城市的天气")
        String fetchWeather(@P("city") String city) {
            return "sunny:" + city;
        }

        @Tool(name = "translate_text", value = "翻译文本")
        String translate(@P("text") String text) {
            return "translated:" + text;
        }
    }

    private static final class ArchivedTools {

        @Tool(name = "archive_report", value = "归档报告")
        String archive(@P("id") String id) {
            return "archived:" + id;
        }
    }
}