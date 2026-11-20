package cn.tolim.pudans.core.ai.library.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiMcpServer;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
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
class McpServerServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    McpClientService mcpClient;

    McpServerService service;

    @BeforeEach
    void setUp() {
        service = new McpServerService(client, mcpClient);
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
                        HttpStatus.BAD_GATEWAY, McpClientService.CODE_REQUEST_FAILED, "连接失败。")));

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
                        .extracting(McpClientService.McpTool::name)
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
                .thenReturn(Mono.just(List.of(new McpClientService.McpTool("search", "Search", schema))));

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
                        HttpStatus.BAD_GATEWAY, McpClientService.CODE_REQUEST_FAILED, "失败。")));

        StepVerifier.create(service.toolCatalog("agent-a"))
                .assertNext(catalog -> assertThat(catalog.tools())
                        .extracting(McpServerService.McpToolDefinition::name)
                        .containsExactly("mcp.mcp-a.search"))
                .verifyComplete();
    }

    @Test
    void rejectsAnUnknownToolPrefixBeforeLoadingServers() {
        StepVerifier.create(service.callTool("agent-a", "other.search", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 404);
                    assertThat(apiException.getCode())
                            .isEqualTo(McpServerService.CODE_TOOL_NOT_FOUND);
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
                            .isEqualTo(McpServerService.CODE_TOOL_NOT_ALLOWED);
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
                .thenReturn(Mono.just(new McpClientService.McpToolCallResult("ok", false)));

        StepVerifier.create(service.callTool("agent-a", "mcp.mcp-a.search", arguments))
                .assertNext(result -> {
                    assertThat(result.output()).isEqualTo("ok");
                    assertThat(result.isError()).isFalse();
                })
                .verifyComplete();
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
                        HttpStatus.BAD_GATEWAY, McpClientService.CODE_REQUEST_FAILED, "无法连接。")));
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
        StepVerifier.create(McpServerService.validate(server))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 400);
                    assertThat(apiException.getCode()).isEqualTo(McpClientService.CODE_INVALID);
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

    private static McpClientService.McpTool tool(String name) {
        return new McpClientService.McpTool(name, name, JsonUtils.mapper().createObjectNode());
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
