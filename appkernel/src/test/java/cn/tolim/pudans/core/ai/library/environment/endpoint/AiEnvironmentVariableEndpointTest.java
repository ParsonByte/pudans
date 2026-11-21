package cn.tolim.pudans.core.ai.library.environment.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiAgent;
import cn.tolim.pudans.contract.ai.AiEnvironmentVariable;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariables;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariables.MaskedEnvironmentVariable;
import cn.tolim.pudans.core.ai.team.Teams;
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
class AiEnvironmentVariableEndpointTest {

    @Mock
    EnvironmentVariables environmentVariables;

    @Mock
    AiEndpointRequests requests;

    @Mock
    Teams teams;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any())).thenReturn(Mono.just("user-1"));
        lenient().when(teams.requireAgent(any(), any())).thenReturn(Mono.just(new AiAgent()));
        client = WebTestClient.bindToRouterFunction(new AiEnvironmentVariableEndpoint(
                                environmentVariables, requests, teams)
                        .endpoint())
                .build();
    }

    @Test
    void listsMaskedVariablesWithoutTheirValues() {
        when(environmentVariables.maskedByAgent("agent-a"))
                .thenReturn(Flux.just(new MaskedEnvironmentVariable(
                        "env-1", "FEISHU_TOKEN", true, "飞书令牌", true, List.of("agent-a"))));

        client.get()
                .uri("/aienvironmentvariables/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("FEISHU_TOKEN")
                .jsonPath("$[0].configured").isEqualTo(true)
                .jsonPath("$[0].value").doesNotExist();
    }

    @Test
    void setsTheVariableValue() {
        when(environmentVariables.updateValue("env-1", "secret", false))
                .thenReturn(Mono.just(variable("secret")));

        client.post()
                .uri("/aienvironmentvariables/env-1/value")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"value\":\"secret\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configured").isEqualTo(true)
                .jsonPath("$.value").doesNotExist();
    }

    @Test
    void clearsTheVariableValue() {
        when(environmentVariables.updateValue("env-1", null, true))
                .thenReturn(Mono.just(variable(null)));

        client.post()
                .uri("/aienvironmentvariables/env-1/value")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"clear\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configured").isEqualTo(false);
    }

    @Test
    void rejectsAMissingAgent() {
        client.get()
                .uri("/aienvironmentvariables/-/by-agent")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsAnEmptyValueUpdateBody() {
        client.post()
                .uri("/aienvironmentvariables/env-1/value")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AiEnvironmentVariable variable(String value) {
        var variable = new AiEnvironmentVariable();
        var metadata = new Metadata();
        metadata.setName("env-1");
        variable.setMetadata(metadata);
        variable.getSpec().setName("FEISHU_TOKEN");
        variable.getSpec().setValue(value);
        variable.getSpec().getAgents().add("agent-a");
        return variable;
    }
}