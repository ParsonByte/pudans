package cn.tolim.pudans.core.ai.runtime.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes.RuntimeView;
import cn.tolim.pudans.core.ai.runtime.SandboxProviderRegistry.ProviderInfo;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxErrors;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecResult;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxFile;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxLogEvent;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxState;
import cn.tolim.pudans.core.infra.exception.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiRuntimeEndpointTest {

    private static final String USER = "user-1";

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    AgentRuntimes agentRuntimes;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any())).thenReturn(Mono.just(USER));
        var endpoint = new AiRuntimeEndpoint(agentRuntimes, requests, JsonUtils.mapper());
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsRuntimesWithThePagingParameters() {
        when(agentRuntimes.list(USER, 123L, 5))
                .thenReturn(Flux.just(view(SandboxState.RUNNING, "sb-1")));

        client.get()
                .uri("/airuntimes?limit=5&beforeId=123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].agent").isEqualTo("agent-1")
                .jsonPath("$[0].state").isEqualTo("RUNNING")
                .jsonPath("$[0].sandboxId").isEqualTo("sb-1");
    }

    @Test
    void getsASingleRuntime() {
        when(agentRuntimes.get("agent-1", USER)).thenReturn(Mono.just(view(SandboxState.STOPPED, null)));

        client.get()
                .uri("/airuntimes/agent-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agent").isEqualTo("agent-1")
                .jsonPath("$.state").isEqualTo("STOPPED");
    }

    @Test
    void listsSandboxProviders() {
        when(agentRuntimes.providers()).thenReturn(Flux.just(new ProviderInfo(
                "ALIYUN",
                "阿里云沙箱",
                "cn-hangzhou",
                null,
                "image-default",
                3600,
                List.of("start", "stop", "restart", "exec", "files", "logs"))));

        client.get()
                .uri("/airuntimes/-/providers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].provider").isEqualTo("ALIYUN")
                .jsonPath("$[0].capabilities[0]").isEqualTo("start");
    }

    @Test
    void startsASandbox() {
        when(agentRuntimes.start("agent-1", USER)).thenReturn(Mono.just(view(SandboxState.RUNNING, "sb-1")));

        client.post()
                .uri("/airuntimes/agent-1/start")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo("RUNNING")
                .jsonPath("$.sandboxId").isEqualTo("sb-1");
    }

    @Test
    void returnsTheLockConflictCodeAsAConflict() {
        when(agentRuntimes.start("agent-1", USER)).thenReturn(Mono.error(
                new ApiException(HttpStatus.CONFLICT, SandboxErrors.LOCKED, "运行时正在被其他操作占用。")));

        client.post()
                .uri("/airuntimes/agent-1/start")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stopsASandbox() {
        when(agentRuntimes.stop("agent-1", USER)).thenReturn(Mono.just(view(SandboxState.STOPPED, null)));

        client.post()
                .uri("/airuntimes/agent-1/stop")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo("STOPPED");
    }

    @Test
    void restartsASandbox() {
        when(agentRuntimes.restart("agent-1", USER)).thenReturn(Mono.just(view(SandboxState.RUNNING, "sb-2")));

        client.post()
                .uri("/airuntimes/agent-1/restart")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sandboxId").isEqualTo("sb-2");
    }

    @Test
    void deletesARuntime() {
        when(agentRuntimes.delete("agent-1", USER)).thenReturn(Mono.empty());

        client.delete()
                .uri("/airuntimes/agent-1")
                .exchange()
                .expectStatus().isNoContent();

        verify(agentRuntimes).delete("agent-1", USER);
    }

    @Test
    void executesACommand() {
        when(agentRuntimes.exec("agent-1", USER, "echo", List.of("hello"), "/workspace", 30))
                .thenReturn(Mono.just(new SandboxExecResult(0, "hello\n", "", 12)));

        client.post()
                .uri("/airuntimes/agent-1/exec")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"command":"echo","args":["hello"],"workdir":"/workspace","timeoutSeconds":30}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.exitCode").isEqualTo(0)
                .jsonPath("$.stdout").isEqualTo("hello\n");
    }

    @Test
    void readsAFile() {
        when(agentRuntimes.readFile("agent-1", USER, "/workspace/a.txt"))
                .thenReturn(Mono.just(new SandboxFile("/workspace/a.txt", "utf-8", "hello", 5)));

        client.get()
                .uri("/airuntimes/agent-1/files?path=/workspace/a.txt")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/workspace/a.txt")
                .jsonPath("$.encoding").isEqualTo("utf-8")
                .jsonPath("$.content").isEqualTo("hello");
    }

    @Test
    void rejectsAFileReadWithoutAPath() {
        client.get()
                .uri("/airuntimes/agent-1/files")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(agentRuntimes);
    }

    @Test
    void writesAFile() {
        when(agentRuntimes.writeFile("agent-1", USER, "/workspace/a.txt", "utf-8", "hello"))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/airuntimes/agent-1/files")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"path":"/workspace/a.txt","encoding":"utf-8","content":"hello"}
                        """)
                .exchange()
                .expectStatus().isNoContent();

        verify(agentRuntimes).writeFile("agent-1", USER, "/workspace/a.txt", "utf-8", "hello");
    }

    @Test
    void streamsLogEvents() {
        when(agentRuntimes.logs("agent-1", USER, false, null))
                .thenReturn(Flux.just(new SandboxLogEvent(NOW, "stdout", "hello")));

        client.get()
                .uri("/airuntimes/agent-1/logs")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectHeader().valueEquals("X-Accel-Buffering", "no")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("event: log")
                        .contains("\"line\":\"hello\"")
                        .contains("\"stream\":\"stdout\""));
    }

    private static RuntimeView view(SandboxState state, String sandboxId) {
        return new RuntimeView(
                "agent-1",
                "ALIYUN",
                state,
                sandboxId,
                "image-agent",
                null,
                1.0,
                1024,
                3600,
                "https://sandbox.example.com",
                null,
                NOW,
                NOW,
                NOW);
    }
}