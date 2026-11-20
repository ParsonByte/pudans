package cn.tolim.pudans.core.ai.library.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiMcpServer;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpApplicationErrorException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class McpClientServiceTest {

    @Mock
    McpClientFactory factory;

    @Mock
    McpClient client;

    private McpClientService service;

    @BeforeEach
    void setUp() {
        service = new McpClientService(factory);
    }

    @Test
    void listsToolsThroughTheLangChain4jClient() {
        var server = server();
        when(factory.create(server)).thenReturn(client);
        when(client.listTools()).thenReturn(
                java.util.List.of(ToolSpecification.builder()
                        .name("search")
                        .description("Search")
                        .parameters(JsonObjectSchema.builder().addStringProperty("q").build())
                        .build()));

        StepVerifier.create(service.listTools(server))
                .assertNext(tools -> {
                    assertThat(tools).singleElement().satisfies(tool -> {
                        assertThat(tool.name()).isEqualTo("search");
                        assertThat(tool.description()).isEqualTo("Search");
                        assertThat(tool.inputSchema()).isNotNull();
                        assertThat(tool.inputSchema().path("properties").path("q").path("type").asText())
                                .isEqualTo("string");
                    });
                })
                .verifyComplete();

        verify(factory).create(server);
    }

    @Test
    void callsAToolThroughTheLangChain4jClient() {
        var server = server();
        when(factory.create(server)).thenReturn(client);
        when(client.executeTool(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.builder().resultText("ok").build());

        StepVerifier.create(service.callTool(server, "search", null))
                .assertNext(result -> {
                    assertThat(result.output()).isEqualTo("ok");
                    assertThat(result.isError()).isFalse();
                })
                .verifyComplete();

        var request = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(client).executeTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("search");
        assertThat(request.getValue().arguments()).isEqualTo("{}");
    }

    @Test
    void keepsApplicationLevelToolFailuresAsToolResults() {
        var server = server();
        when(factory.create(server)).thenReturn(client);
        when(client.executeTool(any(ToolExecutionRequest.class)))
                .thenThrow(new McpApplicationErrorException("tool failed"));

        StepVerifier.create(service.callTool(server, "search", null))
                .assertNext(result -> {
                    assertThat(result.output()).contains("tool failed");
                    assertThat(result.isError()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void reusesAClientUntilTheServerConfigurationChanges() {
        var server = server();
        when(factory.create(server)).thenReturn(client);
        when(client.listTools()).thenReturn(java.util.List.of());

        assertThat(service.listTools(server).block()).isEmpty();
        assertThat(service.listTools(server).block()).isEmpty();

        verify(factory, times(1)).create(server);
    }

    @Test
    void closesTheClientWhenTheServerIsDisabled() throws Exception {
        var server = server();
        when(factory.create(server)).thenReturn(client);
        when(client.listTools()).thenReturn(java.util.List.of());
        service.listTools(server).block();

        service.close("mcp-a");

        verify(client).close();
    }

    @Test
    void mapsClientFailuresToBadGateway() {
        var server = server();
        when(factory.create(server)).thenReturn(client);
        when(client.listTools()).thenThrow(new IllegalStateException("boom"));

        StepVerifier.create(service.listTools(server))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo(McpClientService.CODE_REQUEST_FAILED);
                })
                .verify();
    }

    private static AiMcpServer server() {
        var server = new AiMcpServer();
        var metadata = new Metadata();
        metadata.setName("mcp-a");
        server.setMetadata(metadata);
        server.getSpec().setTransport(AiMcpServer.Transport.STDIO);
        server.getSpec().setCommand("node");
        return server;
    }
}