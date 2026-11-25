package cn.tolim.pudans.core.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiQuotaAssignment;
import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import cn.tolim.pudans.contract.ai.AiQuotaReservation;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaReservationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaUsage;
import cn.tolim.pudans.infra.redis.AiQuotaWindowStore;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiQuotasTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    private static final RequestIdentity IDENTITY = new RequestIdentity("alice", "workspace-a");

    @Mock
    AiQuotaAssignments assignments;

    @Mock
    AiQuotaReservationRepository reservations;

    @Mock
    AiQuotaWindowStore windows;

    private AiQuotas service;

    @BeforeEach
    void setUp() {
        var properties = new AiProperties();
        properties.setReservationTtl(Duration.ofMinutes(15));
        service = new AiQuotas(assignments, reservations, windows, properties);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsAnUnconfiguredSnapshotWithoutAnAssignment() {
        when(assignments.findForIdentity("alice", "workspace-a", Set.of()))
                .thenReturn(Mono.empty());

        var snapshot = service.snapshot(IDENTITY, Set.of()).block();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.fiveHour().configured()).isFalse();
        assertThat(snapshot.fiveHour().remainingPercent()).isZero();
        assertThat(snapshot.weekly().limit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.toolCalls().unit()).isEqualTo("calls");
        assertThat(snapshot.mcp().configured()).isFalse();
    }

    @Test
    void calculatesCreditsAndTheStrictestCallBuckets() {
        var assignment = assignment(NOW.minus(Duration.ofDays(2)));
        var plan = plan();
        when(assignments.findForIdentity("alice", "workspace-a", Set.of()))
                .thenReturn(Mono.just(new AiQuotaAssignments.ResolvedQuota(assignment, plan)));
        when(reservations.fiveHourUsage(
                        "user", "alice", NOW.minus(Duration.ofHours(5))))
                .thenReturn(Mono.just(new AiQuotaUsage(
                        BigDecimal.valueOf(400),
                        50,
                        10,
                        NOW.minus(Duration.ofHours(1)))));
        var weeklyStart = assignment.weeklyAnchorAt();
        when(reservations.weeklyUsage("user", "alice", weeklyStart))
                .thenReturn(Mono.just(new AiQuotaUsage(
                        BigDecimal.valueOf(1000),
                        495,
                        20,
                        NOW.minus(Duration.ofHours(2)))));

        var snapshot = service.snapshot(IDENTITY, Set.of()).block();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.fiveHour().remainingPercent()).isEqualTo(60);
        assertThat(snapshot.weekly().remainingPercent()).isEqualTo(80);
        assertThat(snapshot.toolCalls().remainingPercent()).isEqualTo(1);
        assertThat(snapshot.toolCalls().resetsAt()).isEqualTo(weeklyStart.plus(Duration.ofDays(7)));
        assertThat(snapshot.mcp().remainingPercent()).isEqualTo(80);
    }

    @Test
    void returnsAnExistingReservationForTheSameIdempotencyKey() {
        var existing = reservation(AiSupportValues.RESERVED);
        when(reservations.findByIdempotencyKey("reserve-1")).thenReturn(Mono.just(existing));

        var result = service.reserve(IDENTITY, Set.of(), request()).block();

        assertThat(result).isSameAs(existing);
        verify(windows, never()).reserve(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservesThroughTheRedisWindowAndPersistsTheReservation() {
        var assignment = assignment(NOW.minus(Duration.ofDays(2)));
        var plan = plan();
        when(reservations.findByIdempotencyKey("reserve-1")).thenReturn(Mono.empty());
        when(assignments.findForIdentity("alice", "workspace-a", Set.of()))
                .thenReturn(Mono.just(new AiQuotaAssignments.ResolvedQuota(assignment, plan)));
        when(reservations.settledFiveHourUsage(
                        "user", "alice", NOW.minus(Duration.ofHours(5))))
                .thenReturn(Mono.just(AiQuotaUsage.empty()));
        when(reservations.settledWeeklyUsage("user", "alice", assignment.weeklyAnchorAt()))
                .thenReturn(Mono.just(AiQuotaUsage.empty()));
        when(windows.reserve(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                .thenReturn(Mono.just(AiQuotaWindowStore.ReservationResult.RESERVED));
        when(reservations.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.reserve(IDENTITY, Set.of(), request()).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("reserved");
        assertThat(result.idempotencyKey()).isEqualTo("reserve-1");
        assertThat(result.reservedCredits()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void failsClosedWhenTheQuotaWindowStoreIsUnavailable() {
        var assignment = assignment(NOW.minus(Duration.ofDays(2)));
        var plan = plan();
        when(reservations.findByIdempotencyKey("reserve-1")).thenReturn(Mono.empty());
        when(assignments.findForIdentity("alice", "workspace-a", Set.of()))
                .thenReturn(Mono.just(new AiQuotaAssignments.ResolvedQuota(assignment, plan)));
        when(reservations.settledFiveHourUsage(anyString(), anyString(), any()))
                .thenReturn(Mono.just(AiQuotaUsage.empty()));
        when(reservations.settledWeeklyUsage(anyString(), anyString(), any()))
                .thenReturn(Mono.just(AiQuotaUsage.empty()));
        when(windows.reserve(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));

        StepVerifier.create(service.reserve(IDENTITY, Set.of(), request()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(503);
                            assertThat(api.getCode()).isEqualTo("ai-quota-unavailable");
                        }))
                .verify();
    }

    @Test
    void rejectsAReservationWithoutConfiguredQuota() {
        when(reservations.findByIdempotencyKey("reserve-1")).thenReturn(Mono.empty());
        when(assignments.findForIdentity("alice", "workspace-a", Set.of()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.reserve(IDENTITY, Set.of(), request()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-quota-not-configured")))
                .verify();
    }

    @Test
    void rejectsAReservationWithoutAnIdempotencyKeyReactively() {
        var request = new AiQuotas.ReservationRequest(
                UUID.randomUUID(),
                null,
                BigDecimal.TEN,
                1,
                1,
                " ");

        StepVerifier.create(service.reserve(IDENTITY, Set.of(), request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-quota-idempotency-required")))
                .verify();

        verifyNoInteractions(assignments, reservations, windows);
    }

    @Test
    void rejectsANegativeSettlementReactively() {
        StepVerifier.create(service.settle(UUID.randomUUID(), BigDecimal.ONE, -1, 0))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-quota-demand-invalid")))
                .verify();

        verifyNoInteractions(reservations, windows);
    }

    @Test
    void settlesAndReleasesTheRedisHotWindow() {
        var existing = reservation(AiSupportValues.RESERVED);
        var settled = reservation(AiSupportValues.SETTLED);
        when(reservations.findById(existing.id()))
                .thenReturn(Mono.just(existing));
        when(reservations.settle(existing.id(), BigDecimal.TEN, 1, 1, NOW))
                .thenReturn(Mono.just(settled));
        when(windows.settle(any(), any())).thenReturn(Mono.empty());

        var result = service.settle(existing.id(), BigDecimal.TEN, 1, 1).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("settled");
        verify(windows).settle(any(), any());
    }

    @Test
    void releasesAndRemovesTheRedisHotWindow() {
        var existing = reservation(AiSupportValues.RESERVED);
        var released = reservation(AiSupportValues.RELEASED);
        when(reservations.findById(existing.id())).thenReturn(Mono.just(existing));
        when(reservations.release(existing.id())).thenReturn(Mono.just(released));
        when(windows.release(any(), any())).thenReturn(Mono.empty());

        var result = service.release(existing.id()).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("released");
        verify(windows).release(any(), any());
    }

    private static AiQuotas.ReservationRequest request() {
        return new AiQuotas.ReservationRequest(
                UUID.randomUUID(),
                null,
                BigDecimal.TEN,
                1,
                1,
                "reserve-1");
    }

    private static AiQuotaAssignment assignment(Instant anchor) {
        return new AiQuotaAssignment(
                UUID.randomUUID(),
                "user",
                "alice",
                UUID.randomUUID(),
                anchor,
                null,
                anchor,
                "active",
                anchor,
                anchor);
    }

    private static AiQuotaPlan plan() {
        return new AiQuotaPlan(
                UUID.randomUUID(),
                "test-plan",
                "Test Plan",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(5000),
                100,
                500,
                50,
                200,
                18_000,
                7,
                "assignment_start",
                "active",
                NOW,
                NOW);
    }

    private static AiQuotaReservation reservation(String status) {
        return new AiQuotaReservation(
                UUID.randomUUID(),
                "user",
                "alice",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                BigDecimal.TEN,
                "settled".equals(status) ? BigDecimal.TEN : BigDecimal.ZERO,
                1,
                "settled".equals(status) ? 1 : 0,
                1,
                "settled".equals(status) ? 1 : 0,
                NOW,
                NOW.minus(Duration.ofDays(2)),
                status,
                "reserve-1",
                NOW.plus(Duration.ofMinutes(15)),
                NOW,
                "settled".equals(status) ? NOW : null);
    }

    private static final class AiSupportValues {

        private static final String RESERVED = "reserved";

        private static final String SETTLED = "settled";

        private static final String RELEASED = "released";
    }
}
