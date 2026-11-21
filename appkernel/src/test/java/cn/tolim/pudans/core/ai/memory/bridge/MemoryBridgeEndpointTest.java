package cn.tolim.pudans.core.ai.memory.bridge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MemoryBridgeEndpointTest {

    @Mock
    MemoryBridge bridgeService;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just("user-1"));
        var endpoint = new MemoryBridgeEndpoint(bridgeService, requests);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal())).build()))
                .build();
    }

    @Test
    void searchesAtomicMemoriesForTheBoundSession() {
        when(bridgeService.atomicSearch("user-1", "claude-code", "session-1", "飞书", 5))
                .thenReturn(Mono.just(List.of(new MemoryInjection.Ref(
                        1L, "L1:1", "飞书同步使用 OAuth", MemoryType.WORK_FACT, "集成", 0.8))));

        client.post()
                .uri("/memorybridge/-/atomic/search")
                .header("x-session-id", "session-1")
                .header("x-agent-source", "claude-code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"飞书\",\"limit\":5}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].recordId").isEqualTo("L1:1");
    }

    @Test
    void rejectsARequestWithoutASession() {
        client.post()
                .uri("/memorybridge/-/atomic/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"飞书\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listsScenarios() {
        when(bridgeService.scenarios("user-1", "openai", "session-1")).thenReturn(Flux.empty());

        client.post()
                .uri("/memorybridge/-/scenario/ls")
                .header("x-session-id", "session-1")
                .header("x-agent-source", "openai")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void rejectsAScenarioReadWithoutSceneKey() {
        client.post()
                .uri("/memorybridge/-/scenario/read")
                .header("x-session-id", "session-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static Principal principal() {
        return () -> "alice";
    }
}
