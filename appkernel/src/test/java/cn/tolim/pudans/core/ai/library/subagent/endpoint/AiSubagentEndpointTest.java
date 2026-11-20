package cn.tolim.pudans.core.ai.library.subagent.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.subagent.SubagentService;
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
class AiSubagentEndpointTest {

    @Mock
    SubagentService subagentService;

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
                        new AiSubagentEndpoint(subagentService, requests, teamService).endpoint())
                .build();
    }

    @Test
    void listsSubagentsBoundToTheAgent() {
        when(subagentService.byAgent("agent-a")).thenReturn(Flux.just(subagent()));

        client.get()
                .uri("/aisubagents/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("reviewer")
                .jsonPath("$[0].spec.prompt").isEqualTo("Review the change.");
    }

    @Test
    void rejectsAMissingAgent() {
        client.get()
                .uri("/aisubagents/-/by-agent")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AiSubagent subagent() {
        var subagent = new AiSubagent();
        var metadata = new Metadata();
        metadata.setName("reviewer");
        subagent.setMetadata(metadata);
        subagent.getSpec().setDisplayName("审查者");
        subagent.getSpec().setPrompt("Review the change.");
        subagent.getSpec().getAgents().add("agent-a");
        return subagent;
    }
}