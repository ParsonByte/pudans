package cn.tolim.pudans.core.ai.library.instruction.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiAgent;
import cn.tolim.pudans.contract.ai.AiInstruction;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.instruction.Instructions;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.core.infra.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiInstructionEndpointTest {

    @Mock
    Instructions instructions;

    @Mock
    Teams teams;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(
                        new AiInstructionEndpoint(instructions, teams, requests).endpoint())
                .build();
    }

    @Test
    void listsInstructionsForAnOwnedAgent() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(teams.requireAgent("agent-a", "alice")).thenReturn(Mono.just(agent("agent-a")));
        when(instructions.byAgent("agent-a")).thenReturn(Flux.just(instruction("shared-a")));

        client.get()
                .uri("/aiinstructions/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("shared-a")
                .jsonPath("$[0].spec.scope").isEqualTo("SHARED");
    }

    @Test
    void rejectsAMissingAgentParameter() {
        client.get()
                .uri("/aiinstructions/-/by-agent")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void forbidsAnAgentOutsideTheUserTeam() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(teams.requireAgent("agent-a", "alice"))
                .thenReturn(Mono.error(new AccessDeniedException("无权访问该 Agent。")));

        client.get()
                .uri("/aiinstructions/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static AiInstruction instruction(String name) {
        var instruction = new AiInstruction();
        var metadata = new Metadata();
        metadata.setName(name);
        instruction.setMetadata(metadata);
        instruction.getSpec().setDisplayName(name);
        instruction.getSpec().setContent("规则");
        instruction.getSpec().setScope(AiInstruction.Scope.SHARED);
        return instruction;
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