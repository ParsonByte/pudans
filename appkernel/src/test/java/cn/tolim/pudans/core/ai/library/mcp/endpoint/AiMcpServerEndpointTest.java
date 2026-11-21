package cn.tolim.pudans.core.ai.library.mcp.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiMcpServer;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.mcp.McpClients;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiMcpServerEndpointTest {

    @Mock
    McpServers mcpServers;

    @Mock
    Teams teamService;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(
                        new AiMcpServerEndpoint(mcpServers, teamService, requests).endpoint())
                .build();
    }

    @Test
    void listsServersForAnOwnedAgent() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(teamService.requireAgent("agent-a", "alice")).thenReturn(Mono.just(agent("agent-a")));
        when(mcpServers.byAgent("agent-a")).thenReturn(Flux.just(view()));

        client.get()
                .uri("/aimcpservers/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("mcp-a")
                .jsonPath("$[0].health").isEqualTo("HEALTHY")
                .jsonPath("$[0].toolCount").isEqualTo(1)
                .jsonPath("$[0].tools[0].name").isEqualTo("search");
    }

    @Test
    void rejectsAMissingAgentParameter() {
        client.get()
                .uri("/aimcpservers/-/by-agent")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void forbidsAnAgentOutsideTheUserTeam() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(teamService.requireAgent("agent-a", "alice"))
                .thenReturn(Mono.error(new AccessDeniedException("无权访问该 Agent。")));

        client.get()
                .uri("/aimcpservers/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void probesAServer() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(mcpServers.test("mcp-a")).thenReturn(Mono.just(new McpServers.TestResult(
                true,
                1,
                List.of(new McpClients.McpTool("search", "Search", null)),
                "连通性正常。")));

        client.post()
                .uri("/aimcpservers/mcp-a/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.healthy").isEqualTo(true)
                .jsonPath("$.toolCount").isEqualTo(1)
                .jsonPath("$.tools[0].name").isEqualTo("search");
    }

    @Test
    void requiresAuthenticationForTheTestEndpoint() {
        when(requests.userId(any()))
                .thenReturn(Mono.error(new AccessDeniedException("未认证。")));

        client.post()
                .uri("/aimcpservers/mcp-a/test")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static McpServers.McpServerView view() {
        return new McpServers.McpServerView(
                "mcp-a",
                "MCP A",
                null,
                AiMcpServer.Transport.HTTP,
                true,
                AiMcpServer.Health.HEALTHY,
                1,
                List.of(new McpClients.McpTool("search", "Search", null)),
                null);
    }

    private static AiAgent agent(String name) {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName(name);
        agent.setMetadata(metadata);
        agent.getSpec().setDisplayName(name);
        return agent;
    }
}