package cn.tolim.pudans.core.ai.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiChannel;
import cn.tolim.pudans.contract.ai.dto.AiChannelCreateRequest;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiModelDiscoveries;
import cn.tolim.pudans.core.ai.provider.AiModelImports;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiChannelEndpointTest {

    @Mock
    AiChannels channels;

    @Mock
    ReactiveRequestIdentity identity;

    @Mock
    AiModelDiscoveries discoveries;

    @Mock
    AiModelImports imports;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiChannelEndpoint(channels, discoveries, imports, identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void createsAChannelForTheCurrentWorkspaceWithoutReturningCredentials() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(channels.create(eq("workspace-a"), any(AiChannelCreateRequest.class)))
                .thenReturn(Mono.just(channel()));

        client.post()
                .uri("/aichannels")
                .bodyValue(new AiChannelCreateRequest(
                        "test-provider",
                        "openai-compatible",
                        "https://provider.invalid",
                        "test-secret",
                        Map.of(),
                        Map.of("apiKey", "write-only"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.workspaceId")
                .isEqualTo("workspace-a")
                .jsonPath("$.credentialConfigured")
                .isEqualTo(true)
                .jsonPath("$.credentialCiphertext")
                .doesNotExist();
    }

    @Test
    void listsEveryChannelForAdministrativeInspection() {
        var channel = channel();
        when(channels.listAll()).thenReturn(Flux.just(channel));

        client.get()
                .uri("/aichannels/-/all")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].workspaceId")
                .isEqualTo("workspace-a");

        verify(channels).listAll();
    }

    private static AiChannel channel() {
        return new AiChannel(
                UUID.randomUUID(),
                "workspace-a",
                "test-provider",
                "openai-compatible",
                "https://provider.invalid",
                "test-secret",
                true,
                Map.of("configured", true),
                "active",
                30000,
                1,
                100,
                null,
                Map.of(),
                Instant.now(),
                Instant.now());
    }
}
