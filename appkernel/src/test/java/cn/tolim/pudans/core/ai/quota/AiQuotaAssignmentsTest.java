package cn.tolim.pudans.core.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiQuotaAssignment;
import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import cn.tolim.pudans.contract.ai.dto.AiQuotaAssignmentCreateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaAssignmentRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaPlanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiQuotaAssignmentsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    @Mock
    AiQuotaAssignmentRepository assignments;

    @Mock
    AiQuotaPlanRepository plans;

    private AiQuotaAssignments service;

    @BeforeEach
    void setUp() {
        service = new AiQuotaAssignments(assignments, plans);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void resolvesAUserAssignmentBeforeWorkspaceAndRoles() {
        var user = assignment("user", "alice");
        when(assignments.findActive("user", "alice", NOW)).thenReturn(Mono.just(user));
        when(plans.findById(user.planId())).thenReturn(Mono.just(plan()));

        var result = service.findForIdentity("alice", "workspace-a", Set.of("role-a")).block();

        assertThat(result).isNotNull();
        assertThat(result.assignment().subjectType()).isEqualTo("user");
        verify(assignments, never()).findActive("workspace", "workspace-a", NOW);
    }

    @Test
    void fallsBackToWorkspaceWhenTheUserHasNoAssignment() {
        var workspace = assignment("workspace", "workspace-a");
        when(assignments.findActive("user", "alice", NOW)).thenReturn(Mono.empty());
        when(assignments.findActive("workspace", "workspace-a", NOW))
                .thenReturn(Mono.just(workspace));
        when(plans.findById(workspace.planId())).thenReturn(Mono.just(plan()));

        var result = service.findForIdentity("alice", "workspace-a", Set.of()).block();

        assertThat(result).isNotNull();
        assertThat(result.assignment().subjectType()).isEqualTo("workspace");
    }

    @Test
    void fallsBackToTheMostRecentlyEffectiveRoleAssignment() {
        var workspace = assignment("workspace", "workspace-a");
        var role = assignment("role", "role-a");
        when(assignments.findActive("user", "alice", NOW)).thenReturn(Mono.empty());
        when(assignments.findActive("workspace", "workspace-a", NOW)).thenReturn(Mono.empty());
        when(assignments.findActiveRoles(Set.of("role-a"), NOW)).thenReturn(Flux.just(role));
        when(plans.findById(role.planId())).thenReturn(Mono.just(plan()));

        var result = service.findForIdentity("alice", workspace.subjectId(), Set.of("role-a")).block();

        assertThat(result).isNotNull();
        assertThat(result.assignment().subjectType()).isEqualTo("role");
    }

    @Test
    void rejectsAnUnknownSubjectType() {
        StepVerifier.create(service.resolve("team", "design"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("ai-quota-subject-type-invalid")))
                .verify();
    }

    @Test
    void defaultsTheWeeklyAnchorToTheAssignmentEffectiveTime() {
        var plan = plan();
        when(plans.findById(plan.id())).thenReturn(Mono.just(plan));
        when(assignments.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.create(new AiQuotaAssignmentCreateRequest(
                        "user",
                        "alice",
                        plan.id(),
                        NOW,
                        null,
                        null,
                        null))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.weeklyAnchorAt()).isEqualTo(NOW);
    }

    private static AiQuotaAssignment assignment(String subjectType, String subjectId) {
        return new AiQuotaAssignment(
                UUID.randomUUID(),
                subjectType,
                subjectId,
                UUID.randomUUID(),
                NOW.minusSeconds(3600),
                null,
                NOW.minusSeconds(3600),
                "active",
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3600));
    }

    private static AiQuotaPlan plan() {
        return new AiQuotaPlan(
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
    }
}
