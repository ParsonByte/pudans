package cn.tolim.pudans.core.ai.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiQuotaSnapshot;
import cn.tolim.pudans.core.ai.quota.AiQuotaGuard;
import cn.tolim.pudans.core.ai.quota.AiQuotas;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiQuotaEndpointTest {

    @Mock
    AiQuotas quotas;

    @Mock
    ReactiveRequestIdentity identity;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiQuotaEndpoint(quotas, new AiQuotaGuard(), identity);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void returnsTheCurrentUserSnapshot() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(identity.roles()).thenReturn(Mono.just(Set.of("role-template-view-ai-quota")));
        when(quotas.snapshot(any(RequestIdentity.class), any()))
                .thenReturn(Mono.just(snapshot("credits")));

        client.get()
                .uri("/quotas/-/snapshot")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.fiveHour.configured")
                .isEqualTo(true);
    }

    @Test
    void deniesSubjectInspectionWithoutTheQuotaManagementRole() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(identity.roles()).thenReturn(Mono.just(Set.of("role-template-view-ai-quota")));

        client.get()
                .uri("/quotas/-/snapshot?subjectType=user&subjectId=bob")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void allowsASubjectInspectionForTheQuotaManagementRole() {
        when(identity.resolve(any())).thenReturn(Mono.just(new RequestIdentity("alice", "workspace-a")));
        when(identity.roles()).thenReturn(Mono.just(Set.of(AiQuotaGuard.MANAGE_ROLE)));
        when(quotas.snapshot("user", "bob")).thenReturn(Mono.just(snapshot("calls")));

        client.get()
                .uri("/quotas/-/snapshot?subjectType=user&subjectId=bob")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.toolCalls.unit")
                .isEqualTo("calls");
    }

    private static AiQuotaSnapshot snapshot(String unit) {
        return new AiQuotaSnapshot(
                new AiQuotaSnapshot.AiQuotaBucket(
                        100, "1 小时后", null, BigDecimal.ZERO, BigDecimal.TEN, unit, true),
                new AiQuotaSnapshot.AiQuotaBucket(
                        100, "2026-12-02 10:00", null, BigDecimal.ZERO, BigDecimal.TEN, unit, true),
                new AiQuotaSnapshot.AiQuotaBucket(
                        100, "1 小时后", null, BigDecimal.ZERO, BigDecimal.TEN, "calls", true),
                new AiQuotaSnapshot.AiQuotaBucket(
                        100, "1 小时后", null, BigDecimal.ZERO, BigDecimal.TEN, "calls", true));
    }
}
