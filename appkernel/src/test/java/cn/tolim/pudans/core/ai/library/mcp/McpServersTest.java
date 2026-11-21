package cn.tolim.pudans.core.ai.library.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiMcpServer;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpPromptArgument;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class McpServersTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    McpClients mcpClient;

    McpServers service;

    @BeforeEach
    void setUp() {
        service = new McpServers(client, mcpClient);
    }

    @Test
    void requiresACommandForStdio() {
        var server = server(AiMcpServer.Transport.STDIO);
        server.getSpec().setCommand(null);

        assertInvalid(server, "command");
    }

    @Test
    void requiresAUrlForHttpAndSse() {
        var server = server(AiMcpServer.Transport.HTTP);
        server.getSpec().setUrl(null);

        assertInvalid(server, "url");
    }

    @Test
    void rejectsUrlOnStdioAndCommandOnHttp() {
        var stdio = server(AiMcpServer.Transport.STDIO);
        stdio.getSpec().setCommand("npx");
        stdio.getSpec().setUrl("https://mcp.example.com/mcp");
        assertInvalid(stdio, "不能配置 url");

        var http = server(AiMcpServer.Transport.HTTP);
        http.getSpec().setUrl("https://mcp.example.com/mcp");
        http.getSpec().setCommand("npx");
        assertInvalid(http, "不能配置 command");
    }

    @Test
    void rejectsUnsupportedUrls() {
        var server = server(AiMcpServer.Transport.SSE);
        server.getSpec().setUrl("file:///etc/passwd");

        assertInvalid(server, "http");
    }

    @Test
    void filtersByEnabledAndAgentBinding() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        var disabled = server(AiMcpServer.Transport.HTTP, "mcp-b", false, "agent-a");
        var otherAgent = server(AiMcpServer.Transport.HTTP, "mcp-c", true, "agent-b");
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound, disabled, otherAgent));
        when(mcpClient.listTools(bound)).thenReturn(Mono.just(List.of(tool("search"))));

        StepVerifier.create(service.byAgent("agent-a"))
                .assertNext(view -> {
                    assertThat(view.name()).isEqualTo("mcp-a");
                    assertThat(view.health()).isEqualTo(AiMcpServer.Health.HEALTHY);
                    assertThat(view.toolCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void reportsUnhealthyServersWithoutFailingTheList() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));
        when(mcpClient.listTools(bound))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, McpClients.CODE_REQUEST_FAILED, "连接失败。")));

        StepVerifier.create(service.byAgent("agent-a"))
                .assertNext(view -> {
                    assertThat(view.health()).isEqualTo(AiMcpServer.Health.UNHEALTHY);
                    assertThat(view.message()).isEqualTo("连接失败。");
                    assertThat(view.tools()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void appliesToolAllowlist() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        bound.getSpec().getToolAllowlist().add("search");
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));
        when(mcpClient.listTools(bound)).thenReturn(Mono.just(List.of(tool("search"), tool("delete"))));

        StepVerifier.create(service.byAgent("agent-a"))
                .assertNext(view -> assertThat(view.tools())
                        .extracting(McpClients.McpTool::name)
                        .containsExactly("search"))
                .verifyComplete();
    }

    @Test
    void buildsAGatewayToolCatalogWithPrefixedNames() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        var schema = JsonUtils.mapper().createObjectNode();
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));
        when(mcpClient.listTools(bound))
                .thenReturn(Mono.just(List.of(new McpClients.McpTool("search", "Search", schema))));

        StepVerifier.create(service.toolCatalog("agent-a"))
                .assertNext(catalog -> {
                    assertThat(catalog.tools()).hasSize(1);
                    var definition = catalog.tools().getFirst();
                    assertThat(definition.name()).isEqualTo("mcp.mcp-a.search");
                    assertThat(definition.server()).isEqualTo("mcp-a");
                    assertThat(definition.tool()).isEqualTo("search");
                    assertThat(definition.inputSchema()).isSameAs(schema);
                })
                .verifyComplete();
    }

    @Test
    void skipsUnreachableServersInTheToolCatalog() {
        var reachable = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        var broken = server(AiMcpServer.Transport.SSE, "mcp-b", true, "agent-a");
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(reachable, broken));
        when(mcpClient.listTools(reachable)).thenReturn(Mono.just(List.of(tool("search"))));
        when(mcpClient.listTools(broken))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, McpClients.CODE_REQUEST_FAILED, "失败。")));

        StepVerifier.create(service.toolCatalog("agent-a"))
                .assertNext(catalog -> assertThat(catalog.tools())
                        .extracting(McpServers.McpToolDefinition::name)
                        .containsExactly("mcp.mcp-a.search"))
                .verifyComplete();
    }

    @Test
    void rejectsAnUnknownToolPrefixBeforeLoadingServers() {
        StepVerifier.create(service.callTool("agent-a", "other.search", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 404);
                    assertThat(apiException.getCode())
                            .isEqualTo(McpServers.CODE_TOOL_NOT_FOUND);
                })
                .verify();
    }

    @Test
    void rejectsAToolOutsideTheAllowlist() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        bound.getSpec().getToolAllowlist().add("search");
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));

        StepVerifier.create(service.callTool("agent-a", "mcp.mcp-a.delete", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 403);
                    assertThat(apiException.getCode())
                            .isEqualTo(McpServers.CODE_TOOL_NOT_ALLOWED);
                })
                .verify();
    }

    @Test
    void delegatesToolCallsToTheMcpClient() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        var arguments = JsonUtils.mapper().createObjectNode().put("q", "docs");
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));
        when(mcpClient.callTool(bound, "search", arguments))
                .thenReturn(Mono.just(new McpClients.McpToolCallResult("ok", false)));

        StepVerifier.create(service.callTool("agent-a", "mcp.mcp-a.search", arguments))
                .assertNext(result -> {
                    assertThat(result.output()).isEqualTo("ok");
                    assertThat(result.isError()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void buildsAToolCatalogForExplicitServers() {
        var first = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        var second = server(AiMcpServer.Transport.HTTP, "mcp-b", true, "agent-b");
        when(client.get(AiMcpServer.class, "mcp-a")).thenReturn(Mono.just(first));
        when(client.get(AiMcpServer.class, "mcp-b")).thenReturn(Mono.just(second));
        when(mcpClient.listTools(first)).thenReturn(Mono.just(List.of(tool("search"))));
        when(mcpClient.listTools(second)).thenReturn(Mono.just(List.of(tool("write"))));

        StepVerifier.create(service.toolCatalogFor(List.of("mcp-a", "mcp-b")))
                .assertNext(catalog -> assertThat(catalog.tools())
                        .extracting(McpServers.McpToolDefinition::name)
                        .containsExactlyInAnyOrder("mcp.mcp-a.search", "mcp.mcp-b.write"))
                .verifyComplete();
    }

    @Test
    void delegatesExplicitServerToolCalls() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        when(client.get(AiMcpServer.class, "mcp-a")).thenReturn(Mono.just(bound));
        when(mcpClient.callTool(bound, "search", null))
                .thenReturn(Mono.just(new McpClients.McpToolCallResult("ok", false)));

        StepVerifier.create(service.callToolFor(List.of("mcp-a"), "mcp.mcp-a.search", null))
                .assertNext(result -> {
                    assertThat(result.output()).isEqualTo("ok");
                    assertThat(result.isError()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void buildsStaticToolsWhenTheServerIsUnreachable() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        bound.getSpec().setToolsJson("""
                [{"name":"search","description":"Static search","inputSchema":{"type":"object"}}]
                """);
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));
        when(mcpClient.listTools(bound))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, McpClients.CODE_REQUEST_FAILED, "失败。")));

        StepVerifier.create(service.toolCatalog("agent-a"))
                .assertNext(catalog -> {
                    assertThat(catalog.tools()).singleElement().satisfies(definition -> {
                        assertThat(definition.name()).isEqualTo("mcp.mcp-a.search");
                        assertThat(definition.description()).isEqualTo("Static search");
                        assertThat(definition.inputSchema().path("type").asText())
                                .isEqualTo("object");
                    });
                })
                .verifyComplete();
    }

    @Test
    void rejectsInvalidStaticToolJson() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        bound.getSpec().setToolsJson("{}");

        assertInvalid(bound, "toolsJson");
    }

    @Test
    void acceptsWebSocketTransport() {
        var bound = server(AiMcpServer.Transport.WEBSOCKET, "mcp-a", true, "agent-a");
        bound.getSpec().setUrl("wss://mcp.example.com/mcp");

        StepVerifier.create(McpServers.validate(bound)).verifyComplete();
    }

    @Test
    void decoratesToolkitsWithResourceAndPromptTools() {
        var bound = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        var clientMock = org.mockito.Mockito.mock(McpClient.class);
        when(client.listAll(eq(AiMcpServer.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));
        when(mcpClient.clients(List.of(bound))).thenReturn(Mono.just(List.of(clientMock)));
        when(mcpClient.listPrompts(bound)).thenReturn(Mono.just(List.of(
                new McpPrompt("review", "Review prompt",
                        List.of(new McpPromptArgument("topic", "Topic", true))))));

        var toolkit = service.decorateToolkit(new HarnessToolkit(), "agent-a").block();

        assertThat(toolkit).isNotNull();
        assertThat(toolkit.specifications()).extracting(specification -> specification.name())
                .contains(
                        "mcp_list_resources",
                        "mcp_get_resource",
                        "mcp_prompt_list",
                        "mcp_prompt_get");
        var prompts = toolkit.execute(ToolExecutionRequest.builder()
                .name("mcp_prompt_list")
                .arguments("{\"server\":\"mcp-a\"}")
                .build());
        assertThat(prompts.error()).isFalse();
        assertThat(prompts.output()).contains("review").contains("topic");
    }

    @Test
    void persistsHealthyStatusAfterATest() {
        var server = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        when(client.get(AiMcpServer.class, "mcp-a")).thenReturn(Mono.just(server));
        when(mcpClient.listTools(server)).thenReturn(Mono.just(List.of(tool("search"))));
        when(client.update(server)).thenReturn(Mono.just(server));

        StepVerifier.create(service.test("mcp-a"))
                .assertNext(result -> {
                    assertThat(result.healthy()).isTrue();
                    assertThat(result.toolCount()).isEqualTo(1);
                    assertThat(result.message()).isEqualTo("连通性正常。");
                })
                .verifyComplete();

        assertThat(server.getStatus().getHealth()).isEqualTo(AiMcpServer.Health.HEALTHY);
        assertThat(server.getStatus().getLastCheckedAt()).isNotNull();
    }

    @Test
    void persistsUnhealthyStatusAfterAFailedTest() {
        var server = server(AiMcpServer.Transport.HTTP, "mcp-a", true, "agent-a");
        when(client.get(AiMcpServer.class, "mcp-a")).thenReturn(Mono.just(server));
        when(mcpClient.listTools(server))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, McpClients.CODE_REQUEST_FAILED, "无法连接。")));
        when(client.update(server)).thenReturn(Mono.just(server));

        StepVerifier.create(service.test("mcp-a"))
                .assertNext(result -> {
                    assertThat(result.healthy()).isFalse();
                    assertThat(result.tools()).isEmpty();
                    assertThat(result.message()).isEqualTo("无法连接。");
                })
                .verifyComplete();

        assertThat(server.getStatus().getHealth()).isEqualTo(AiMcpServer.Health.UNHEALTHY);
    }

    @Test
    void closesTheStdioSessionWhenDisabled() {
        var server = server(AiMcpServer.Transport.STDIO, "mcp-a", true, "agent-a");
        when(client.get(AiMcpServer.class, "mcp-a")).thenReturn(Mono.just(server));
        when(client.update(server)).thenReturn(Mono.just(server));

        StepVerifier.create(service.setEnabled("mcp-a", false))
                .assertNext(updated -> assertThat(updated.getSpec().isEnabled()).isFalse())
                .verifyComplete();

        verify(mcpClient).close("mcp-a");
    }

    private static void assertInvalid(AiMcpServer server, String messagePart) {
        StepVerifier.create(McpServers.validate(server))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 400);
                    assertThat(apiException.getCode()).isEqualTo(McpClients.CODE_INVALID);
                    assertThat(apiException.getReason()).contains(messagePart);
                })
                .verify();
    }

    private static ApiException assertApiException(Throwable error, int status) {
        assertThat(error).isInstanceOf(ApiException.class);
        var apiException = (ApiException) error;
        assertThat(apiException.getStatusCode().value()).isEqualTo(status);
        return apiException;
    }

    private static McpClients.McpTool tool(String name) {
        return new McpClients.McpTool(name, name, JsonUtils.mapper().createObjectNode());
    }

    private static AiMcpServer server(AiMcpServer.Transport transport) {
        return server(transport, "mcp-a", true, "agent-a");
    }

    private static AiMcpServer server(
            AiMcpServer.Transport transport, String name, boolean enabled, String agent) {
        var server = new AiMcpServer();
        var metadata = new Metadata();
        metadata.setName(name);
        server.setMetadata(metadata);
        server.getSpec().setDisplayName(name);
        server.getSpec().setTransport(transport);
        server.getSpec().setEnabled(enabled);
        server.getSpec().getAgents().add(agent);
        if (transport != AiMcpServer.Transport.STDIO) {
            server.getSpec().setUrl("https://mcp.example.com/mcp");
        } else {
            server.getSpec().setCommand("npx");
        }
        return server;
    }
}
