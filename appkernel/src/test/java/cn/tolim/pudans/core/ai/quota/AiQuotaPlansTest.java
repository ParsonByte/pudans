package cn.tolim.pudans.core.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiQuotaPlanCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiQuotaPlanUpdateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaAssignmentRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaPlanRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaReservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiQuotaPlansTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    @Mock
    AiQuotaPlanRepository plans;

    @Mock
    AiQuotaAssignmentRepository assignments;

    @Mock
    AiQuotaReservationRepository reservations;

    private AiQuotaPlans service;

    @BeforeEach
    void setUp() {
        service = new AiQuotaPlans(plans, assignments, reservations);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAPlanWithFixedWindowsAndActiveStatus() {
        when(plans.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.create(request(" ")).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("active");
        assertThat(result.fiveHourWindowSeconds()).isEqualTo(18_000);
        assertThat(result.weeklyWindowDays()).isEqualTo(7);
        assertThat(result.weeklyAnchorMode()).isEqualTo("assignment_start");
    }

    @Test
    void rejectsAnUnsupportedStatus() {
        StepVerifier.create(service.create(request("paused")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-quota-status-invalid")))
                .verify();

        verify(plans, never()).insert(any());
    }

    @Test
    void ignoresABlankStatusOnUpdate() {
        var existing = new cn.tolim.pudans.contract.ai.AiQuotaPlan(
                UUID.randomUUID(),
                "test-plan",
                "Test Plan",
                BigDecimal.TEN,
                BigDecimal.valueOf(100),
                10,
                100,
                10,
                100,
                18_000,
                7,
                "assignment_start",
                "active",
                NOW,
                NOW);
        when(plans.findById(existing.id())).thenReturn(Mono.just(existing));
        when(plans.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.update(existing.id(), updateRequest(" ")).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("active");
    }

    @Test
    void rejectsAnUnsupportedStatusOnUpdate() {
        var existing = new cn.tolim.pudans.contract.ai.AiQuotaPlan(
                UUID.randomUUID(),
                "test-plan",
                "Test Plan",
                BigDecimal.TEN,
                BigDecimal.valueOf(100),
                10,
                100,
                10,
                100,
                18_000,
                7,
                "assignment_start",
                "active",
                NOW,
                NOW);
        when(plans.findById(existing.id())).thenReturn(Mono.just(existing));

        StepVerifier.create(service.update(existing.id(), updateRequest("paused")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-quota-status-invalid")))
                .verify();

        verify(plans, never()).update(any());
    }

    private static AiQuotaPlanCreateRequest request(String status) {
        return new AiQuotaPlanCreateRequest(
                "test-plan",
                "Test Plan",
                BigDecimal.TEN,
                BigDecimal.valueOf(100),
                10,
                100,
                10,
                100,
                null,
                null,
                null,
                status);
    }

    private static AiQuotaPlanUpdateRequest updateRequest(String status) {
        return new AiQuotaPlanUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status);
    }
}
