package cn.tolim.pudans.core.ai.memory.governance;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OrgMemoryEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    OrgMemory service;

    @Mock
    Accounts accounts;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new OrgMemoryEndpoint(service, new AiEndpointRequests(accounts));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void listsApprovedMemoriesWithLineage() {
        when(service.list(Long.MAX_VALUE, 20))
                .thenReturn(Mono.just(List.of(orgMemory(1L, "VERIFIED", true))));

        client.get()
                .uri("/orgmemories")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].trustLevel").isEqualTo("VERIFIED")
                .jsonPath("$[0].sourceTeamIds[0]").isEqualTo("team-a")
                .jsonPath("$[0].sourceMemoryIds[0]").isEqualTo(11);
    }

    @Test
    void listsPendingMemories() {
        when(service.pending(7L, 10))
                .thenReturn(Mono.just(List.of(orgMemory(2L, "PENDING", true))));

        client.get()
                .uri("/orgmemories/-/pending?beforeId=7&limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].trustLevel").isEqualTo("PENDING");
    }

    @Test
    void approvesAMemoryWithTheCurrentActor() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.approve(5L, USER_ID)).thenReturn(Mono.just(orgMemory(5L, "VERIFIED", true)));

        client.post()
                .uri("/orgmemories/5/approve")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.trustLevel").isEqualTo("VERIFIED");
    }

    @Test
    void revokesAMemory() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.revoke(5L, USER_ID)).thenReturn(Mono.just(orgMemory(5L, "REVOKED", false)));

        client.post()
                .uri("/orgmemories/5/revoke")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.trustLevel").isEqualTo("REVOKED")
                .jsonPath("$.active").isEqualTo(false);
    }

    @Test
    void rejectsAMalformedIdentifier() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));

        client.post()
                .uri("/orgmemories/abc/approve")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void returnsNotFoundForAMissingOrgMemory() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.revoke(9L, USER_ID)).thenReturn(Mono.error(new ApiException(
                HttpStatus.NOT_FOUND, OrgMemory.CODE_ORG_MEMORY_NOT_FOUND, "组织记忆不存在。")));

        client.post()
                .uri("/orgmemories/9/revoke")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsBadRequestForAnInvalidTransition() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(service.approve(5L, USER_ID)).thenReturn(Mono.error(new ApiException(
                HttpStatus.BAD_REQUEST,
                MemoryGovernance.CODE_REQUEST_INVALID,
                "组织记忆当前状态不允许该操作。")));

        client.post()
                .uri("/orgmemories/5/approve")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }

    private static OrgMemoryRecord orgMemory(long id, String trustLevel, boolean active) {
        return new OrgMemoryRecord(
                id,
                "记忆内容-" + id,
                "WORK_FACT",
                "hash-" + id,
                trustLevel,
                "SINGLE",
                List.of("team-a"),
                List.of("user-1"),
                List.of(11L),
                1,
                "approver",
                CREATED_AT,
                active,
                CREATED_AT,
                CREATED_AT);
    }
}
