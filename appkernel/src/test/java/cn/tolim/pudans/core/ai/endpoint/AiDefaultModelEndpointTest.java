package cn.tolim.pudans.core.ai.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiDefaultModel;
import cn.tolim.pudans.core.ai.AiDefaultModels;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AiDefaultModelEndpointTest {

    @Mock
    AiDefaultModels defaults;

    @Mock
    ReactiveRequestIdentity identity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiDefaultModelEndpoint(defaults, identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsEveryDefaultModelForAdministrativeInspection() {
        var model = new AiDefaultModel(
                UUID.randomUUID(),
                "workspace-b",
                "chat",
                UUID.randomUUID(),
                Instant.parse("2026-11-25T08:00:00Z"),
                Instant.parse("2026-11-25T08:00:00Z"));
        when(defaults.listAll()).thenReturn(Flux.just(model));

        client.get()
                .uri("/aidefaultmodels/-/all")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].workspaceId")
                .isEqualTo("workspace-b");

        verify(defaults).listAll();
    }
}
