package cn.tolim.pudans.core.ai.library.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiMcpServer;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiMcpServerReconcilerTest {

    @Mock
    ExtensionClient client;

    @Mock
    McpClients mcpClients;

    private AiMcpServerReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new AiMcpServerReconciler(client, mcpClients);
    }

    @Test
    void closesClientsAndRemovesTheFinalizerOnDelete() {
        var server = server();
        server.getMetadata().setDeletionTimestamp(Instant.now());
        server.getMetadata().setFinalizers(java.util.Set.of("ai-mcp-server-lifecycle"));
        when(client.fetch(AiMcpServer.class, "mcp-a")).thenReturn(Optional.of(server));

        reconciler.reconcile(new Reconciler.Request("mcp-a"));

        verify(mcpClients).close("mcp-a");
        assertThat(server.getMetadata().getFinalizers())
                .doesNotContain("ai-mcp-server-lifecycle");
        verify(client).update(server);
    }

    @Test
    void addsTheFinalizerToAnActiveServer() {
        var server = server();
        when(client.fetch(AiMcpServer.class, "mcp-a")).thenReturn(Optional.of(server));

        reconciler.reconcile(new Reconciler.Request("mcp-a"));

        assertThat(server.getMetadata().getFinalizers())
                .contains("ai-mcp-server-lifecycle");
        verify(client).update(server);
        verify(mcpClients, never()).close(any());
    }

    private static AiMcpServer server() {
        var server = new AiMcpServer();
        var metadata = new Metadata();
        metadata.setName("mcp-a");
        server.setMetadata(metadata);
        server.getSpec().setDisplayName("MCP A");
        server.getSpec().setTransport(AiMcpServer.Transport.HTTP);
        server.getSpec().setUrl("https://mcp.example.com/mcp");
        return server;
    }
}