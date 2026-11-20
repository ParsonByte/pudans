package cn.tolim.pudans.core.ai.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarnessToolkitFactoryTest {

    private final HarnessToolkitFactory factory = new HarnessToolkitFactory();

    @Test
    void groupsMcpToolsAndRegistersAsyncPatterns() {
        var toolkit = factory.create(
                List.of(
                        tool("mcp.a.search"),
                        tool("mcp.b.write"),
                        tool("mcp.a.long_run")),
                (request, arguments) -> new HarnessToolkit.ToolResult("ok", false),
                true,
                List.of("mcp.*.long_*"));

        assertThat(toolkit.groupManager().get("mcp.a")).isNotNull();
        assertThat(toolkit.groupManager().get("mcp.b")).isNotNull();
        assertThat(toolkit.groupManager().metaGroupNames())
                .containsExactlyInAnyOrder("mcp.a", "mcp.b");
        assertThat(toolkit.hasAsyncTools()).isTrue();
        assertThat(toolkit.specifications()).extracting(specification -> specification.name())
                .containsExactlyInAnyOrder("mcp.a.search", "mcp.b.write", "mcp.a.long_run");
        assertThat(toolkit.search("search", 5).toolNames()).containsExactly("mcp.a.search");
    }

    @Test
    void skipsGroupsAndAsyncRegistrationWhenDisabled() {
        var toolkit = factory.create(
                List.of(tool("mcp.a.search")),
                (request, arguments) -> new HarnessToolkit.ToolResult("ok", false),
                false,
                List.of());

        assertThat(toolkit.groupManager().groups()).isEmpty();
        assertThat(toolkit.hasAsyncTools()).isFalse();
        assertThat(toolkit.asyncRegistry()).isNull();
    }

    @Test
    void executesAsyncToolsThroughTheRegistry() {
        var toolkit = factory.create(
                List.of(tool("mcp.a.long_run")),
                (request, arguments) -> new HarnessToolkit.ToolResult("done", false),
                false,
                List.of("mcp.*.long_*"));

        var result = toolkit.execute(ToolExecutionRequest.builder()
                .name("mcp.a.long_run")
                .arguments("{}")
                .build());

        assertThat(result.error()).isFalse();
        assertThat(result.output()).contains("后台执行");
    }

    private static AiTool tool(String name) {
        return new AiTool(
                name,
                "Tool " + name,
                JsonUtils.mapper().createObjectNode().put("type", "object"),
                null);
    }
}