package cn.tolim.pudans.core.ai.memory.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryGovernanceRepository.OrgMemoryRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OrgMemoryTest {

    private static final String REVIEWER = "admin-1";

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiMemoryGovernanceRepository repository;

    @InjectMocks
    OrgMemory service;

    @Test
    void listsVerifiedMemoriesWithLineage() {
        when(repository.listOrgMemories("VERIFIED", 9L, 20))
                .thenReturn(Flux.just(orgMemory(1L, "VERIFIED", true)));

        StepVerifier.create(service.list(9L, 20))
                .assertNext(items -> {
                    assertThat(items).hasSize(1);
                    assertThat(items.get(0).trustLevel()).isEqualTo("VERIFIED");
                    assertThat(items.get(0).sourceTeamIds()).containsExactly("team-a");
                    assertThat(items.get(0).sourceMemoryIds()).containsExactly(11L);
                })
                .verifyComplete();
    }

    @Test
    void listsPendingMemories() {
        when(repository.listOrgMemories("PENDING", Long.MAX_VALUE, 50))
                .thenReturn(Flux.just(orgMemory(2L, "PENDING", true)));

        StepVerifier.create(service.pending(Long.MAX_VALUE, 50))
                .assertNext(items -> assertThat(items.get(0).trustLevel()).isEqualTo("PENDING"))
                .verifyComplete();
    }

    @Test
    void approvesAPendingMemory() {
        when(repository.findOrgMemory(1L)).thenReturn(Mono.just(orgMemory(1L, "PENDING", true)));
        when(repository.updateOrgMemoryStatus(1L, "VERIFIED", true, REVIEWER, true))
                .thenReturn(Mono.just(orgMemory(1L, "VERIFIED", true)));

        StepVerifier.create(service.approve(1L, REVIEWER))
                .assertNext(memory -> assertThat(memory.trustLevel()).isEqualTo("VERIFIED"))
                .verifyComplete();
    }

    @Test
    void rejectsAPendingMemory() {
        when(repository.findOrgMemory(1L)).thenReturn(Mono.just(orgMemory(1L, "PENDING", true)));
        when(repository.updateOrgMemoryStatus(1L, "REJECTED", false, REVIEWER, false))
                .thenReturn(Mono.just(orgMemory(1L, "REJECTED", false)));

        StepVerifier.create(service.reject(1L, REVIEWER))
                .assertNext(memory -> {
                    assertThat(memory.trustLevel()).isEqualTo("REJECTED");
                    assertThat(memory.active()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void revokesAVerifiedMemoryButKeepsLineage() {
        when(repository.findOrgMemory(1L)).thenReturn(Mono.just(orgMemory(1L, "VERIFIED", true)));
        when(repository.updateOrgMemoryStatus(1L, "REVOKED", false, REVIEWER, false))
                .thenReturn(Mono.just(orgMemory(1L, "REVOKED", false)));

        StepVerifier.create(service.revoke(1L, REVIEWER))
                .assertNext(memory -> {
                    assertThat(memory.trustLevel()).isEqualTo("REVOKED");
                    assertThat(memory.sourceMemoryIds()).containsExactly(11L);
                })
                .verifyComplete();
    }

    @Test
    void rejectsAnInvalidTransition() {
        when(repository.findOrgMemory(1L)).thenReturn(Mono.just(orgMemory(1L, "REJECTED", false)));

        StepVerifier.create(service.approve(1L, REVIEWER))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 400, MemoryGovernance.CODE_REQUEST_INVALID))
                .verify();
    }

    @Test
    void returnsNotFoundForAMissingOrgMemory() {
        when(repository.findOrgMemory(9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.revoke(9L, REVIEWER))
                .expectErrorSatisfies(error -> assertApiError(
                        error, 404, OrgMemory.CODE_ORG_MEMORY_NOT_FOUND))
                .verify();
    }

    private static void assertApiError(Throwable error, int status, String code) {
        assertThat(error).isInstanceOf(ApiException.class);
        var api = (ApiException) error;
        assertThat(api.getStatusCode().value()).isEqualTo(status);
        assertThat(api.getCode()).isEqualTo(code);
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
