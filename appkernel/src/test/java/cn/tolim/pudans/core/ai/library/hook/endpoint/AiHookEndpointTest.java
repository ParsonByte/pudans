package cn.tolim.pudans.core.ai.library.hook.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.hook.HookService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiHookEndpointTest {

    @Mock
    HookService hookService;

    @Mock
    AiEndpointRequests requests;

    @Mock
    AiTeamService teamService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any())).thenReturn(Mono.just("user-1"));
        lenient().when(teamService.requireAgent(any(), any())).thenReturn(Mono.just(new AiAgent()));
        client = WebTestClient.bindToRouterFunction(
                        new AiHookEndpoint(hookService, requests, teamService).endpoint())
                .build();
    }

    @Test
    void listsHooksBoundToTheAgent() {
        when(hookService.byAgent("agent-a", null)).thenReturn(Flux.just(hook()));

        client.get()
                .uri("/aihooks/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("hook-a")
                .jsonPath("$[0].spec.event").isEqualTo("PRE_TOOL_USE");
    }

    @Test
    void filtersByTheParsedEvent() {
        when(hookService.byAgent("agent-a", AiHook.Event.PRE_TOOL_USE)).thenReturn(Flux.just(hook()));

        client.get()
                .uri("/aihooks/-/by-agent?agent=agent-a&event=pre_tool_use")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("hook-a");
    }

    @Test
    void rejectsAMissingAgent() {
        client.get()
                .uri("/aihooks/-/by-agent")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsAnUnknownEvent() {
        client.get()
                .uri("/aihooks/-/by-agent?agent=agent-a&event=on-message")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AiHook hook() {
        var hook = new AiHook();
        var metadata = new Metadata();
        metadata.setName("hook-a");
        hook.setMetadata(metadata);
        hook.getSpec().setDisplayName("Hook A");
        hook.getSpec().setEvent(AiHook.Event.PRE_TOOL_USE);
        hook.getSpec().setMatcher("Read");
        hook.getSpec().setCommand("notify");
        hook.getSpec().getAgents().add("agent-a");
        return hook;
    }
}