package cn.tolim.pudans.core.ai.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiCreationCatalog;
import cn.tolim.pudans.core.ai.catalog.AiCatalogs;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
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
class AiCatalogEndpointTest {

    @Mock
    AiCatalogs catalogs;

    @Mock
    ReactiveRequestIdentity identity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiCatalogEndpoint(catalogs, identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void returnsAnEmptyModelArrayWithoutAvailableModels() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(catalogs.models("workspace-a")).thenReturn(Flux.empty());

        client.get()
                .uri("/catalogs/-/models")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
    }

    @Test
    void returnsAnEmptyCreationCatalogWithoutConfiguredEntries() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(catalogs.creation("workspace-a"))
                .thenReturn(Mono.just(new AiCreationCatalog(List.of())));

        client.get()
                .uri("/catalogs/-/creation")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.mediaModes.length()")
                .isEqualTo(0);
    }
}
