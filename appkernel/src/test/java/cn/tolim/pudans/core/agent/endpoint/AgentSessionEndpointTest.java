package cn.tolim.pudans.core.agent.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.AgentSession;
import cn.tolim.pudans.contract.agent.dto.AgentSessionPage;
import cn.tolim.pudans.contract.agent.dto.AgentSessionSummary;
import cn.tolim.pudans.core.agent.runtime.AgentSessions;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AgentSessionEndpointTest {

    @Mock
    AgentSessions sessions;

    @Mock
    ReactiveRequestIdentity identity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AgentSessionEndpoint(sessions, identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void createsASessionForTheCurrentOwner() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(sessions.create(any(), any())).thenReturn(Mono.just(session()));

        client.post()
                .uri("/agentsessions/-/create")
                .header("Content-Type", "application/json")
                .bodyValue("{\"title\":\"Planning\"}")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Planning")
                .jsonPath("$.ownerId")
                .isEqualTo("alice");
    }

    @Test
    void readsSessionMessages() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(sessions.messages(any(), any(), any(), anyInt()))
                .thenReturn(Mono.just(new cn.tolim.pudans.contract.agent.dto.AgentMessagePage(
                        java.util.List.of(), null)));

        client.get()
                .uri("/agentsessions/{id}/messages", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items")
                .isArray();
    }

    @Test
    void listsEverySessionForAdministrativeInspection() {
        var session = session();
        when(sessions.listAll(100)).thenReturn(Mono.just(new AgentSessionPage(java.util.List.of(session), 100)));

        client.get()
                .uri("/agentsessions/-/all?limit=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].ownerId")
                .isEqualTo("alice");

        org.mockito.Mockito.verify(sessions).listAll(100);
    }

    @Test
    void summarizesEverySessionForAdministrativeInspection() {
        when(sessions.summaryAll())
                .thenReturn(Mono.just(new AgentSessionSummary(3, 2, Instant.now())));

        client.get()
                .uri("/agentsessions/-/all-summary")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(3)
                .jsonPath("$.active")
                .isEqualTo(2);
    }

    private static AgentSession session() {
        return new AgentSession(
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                "Planning",
                null,
                null,
                "active",
                null,
                Instant.now(),
                Instant.now());
    }
}
