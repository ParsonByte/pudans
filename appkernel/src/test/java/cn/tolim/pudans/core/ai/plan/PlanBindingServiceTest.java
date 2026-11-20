package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiPlanBinding;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PlanBindingServiceTest {

    private static final String USER_ID = "uid-alice";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PlanCatalogService catalogService;

    @Mock
    UserGroupService userGroupService;

    @Mock
    UserService userService;

    @Mock
    PlanResolutionService resolutionService;

    private PlanBindingService service;

    @BeforeEach
    void setUp() {
        service = new PlanBindingService(
                client, catalogService, userGroupService, userService, resolutionService);
        service.setClock(Clock.fixed(PlanTestFixtures.NOW, ZoneOffset.UTC));
        lenient()
                .when(catalogService.requireEnabled("standard"))
                .thenReturn(Mono.just(PlanTestFixtures.plan("standard")));
        lenient()
                .when(client.listAll(eq(AiPlanBinding.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.empty());
        lenient()
                .when(client.get(eq(AiPlanBinding.class), anyString()))
                .thenReturn(Mono.empty());
        lenient()
                .when(client.create(any(AiPlanBinding.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient()
                .when(client.update(any(AiPlanBinding.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient()
                .when(client.delete(any(AiPlanBinding.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void createsBindingsWithDeterministicNames() {
        when(userGroupService.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));

        StepVerifier.create(service.bulk(request(
                        List.of(new PlanBindingService.BindingSubject(
                                AiPlanBinding.SubjectKind.UserGroup, "rd")))))
                .assertNext(result -> {
                    assertThat(result.plan()).isEqualTo("standard");
                    assertThat(result.bindings()).containsExactly(
                            new PlanBindingService.BindingSummary(
                                    "aiplanbinding-usergroup-rd",
                                    "standard",
                                    AiPlanBinding.SubjectKind.UserGroup,
                                    "rd",
                                    3,
                                    null,
                                    null));
                })
                .verifyComplete();

        var created = ArgumentCaptor.forClass(AiPlanBinding.class);
        verify(client).create(created.capture());
        assertThat(created.getValue().getMetadata().getName())
                .isEqualTo("aiplanbinding-usergroup-rd");
        assertThat(created.getValue().getSpec().getSubject().getKind())
                .isEqualTo(AiPlanBinding.SubjectKind.UserGroup);
        assertThat(created.getValue().getSpec().getSubject().getName()).isEqualTo("rd");
        verify(resolutionService).invalidateAll();
    }

    @Test
    void validatesUserSubjects() {
        when(userService.getUserByUid(USER_ID)).thenReturn(Mono.just(new User()));

        StepVerifier.create(service.bulk(request(
                        List.of(new PlanBindingService.BindingSubject(
                                AiPlanBinding.SubjectKind.User, USER_ID)))))
                .assertNext(result -> assertThat(result.bindings().get(0).name())
                        .isEqualTo("aiplanbinding-user-" + USER_ID))
                .verifyComplete();
    }

    @Test
    void replacesAnExistingBindingAndDeletesDuplicates() {
        var primary = PlanTestFixtures.binding(
                "aiplanbinding-usergroup-rd",
                AiPlanBinding.SubjectKind.UserGroup,
                "rd",
                "old-plan",
                0);
        var duplicate = PlanTestFixtures.binding(
                "aiplanbinding-usergroup-rd-copy",
                AiPlanBinding.SubjectKind.UserGroup,
                "rd",
                "old-plan",
                0);
        when(userGroupService.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));
        when(client.listAll(eq(AiPlanBinding.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(duplicate, primary));

        StepVerifier.create(service.bulk(request(
                        List.of(new PlanBindingService.BindingSubject(
                                AiPlanBinding.SubjectKind.UserGroup, "rd")))))
                .assertNext(result -> assertThat(result.bindings().get(0).name())
                        .isEqualTo("aiplanbinding-usergroup-rd"))
                .verifyComplete();

        assertThat(primary.getSpec().getPlan()).isEqualTo("standard");
        assertThat(primary.getSpec().getPriority()).isEqualTo(3);
        verify(client).update(primary);
        verify(client).delete(duplicate);
    }

    @Test
    void rejectsAnEffectiveBindingWhenReplacementIsDisabled() {
        when(userGroupService.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));
        when(client.listAll(eq(AiPlanBinding.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(PlanTestFixtures.binding(
                        "aiplanbinding-usergroup-rd",
                        AiPlanBinding.SubjectKind.UserGroup,
                        "rd",
                        "old-plan",
                        0)));

        var request = new PlanBindingService.BulkBindRequest(
                "standard",
                List.of(new PlanBindingService.BindingSubject(
                        AiPlanBinding.SubjectKind.UserGroup, "rd")),
                0,
                false);

        StepVerifier.create(service.bulk(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanBindingService.CODE_BINDING_CONFLICT))
                .verify();

        verify(client, never()).update(any(AiPlanBinding.class));
        verify(client, never()).create(any(AiPlanBinding.class));
    }

    @Test
    void ignoresBindingsThatAreNotEffectiveYet() {
        var future = PlanTestFixtures.binding(
                "aiplanbinding-usergroup-rd",
                AiPlanBinding.SubjectKind.UserGroup,
                "rd",
                "old-plan",
                0);
        future.getSpec().setEffectiveFrom(PlanTestFixtures.NOW.plusSeconds(60));
        when(userGroupService.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));
        when(client.listAll(eq(AiPlanBinding.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(future));

        StepVerifier.create(service.bulk(request(
                        List.of(new PlanBindingService.BindingSubject(
                                AiPlanBinding.SubjectKind.UserGroup, "rd")))))
                .assertNext(result -> assertThat(result.bindings().get(0).name())
                        .isEqualTo("aiplanbinding-usergroup-rd"))
                .verifyComplete();

        verify(client).create(any(AiPlanBinding.class));
        verify(client, never()).update(any(AiPlanBinding.class));
    }

    @Test
    void rejectsAnUnknownUserGroup() {
        when(userGroupService.getUserGroup("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.bulk(request(
                        List.of(new PlanBindingService.BindingSubject(
                                AiPlanBinding.SubjectKind.UserGroup, "missing")))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanBindingService.CODE_BINDING_INVALID))
                .verify();

        verify(client, never()).create(any(AiPlanBinding.class));
    }

    @Test
    void rejectsAnUnknownUser() {
        when(userService.getUserByUid("uid-missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.bulk(request(
                        List.of(new PlanBindingService.BindingSubject(
                                AiPlanBinding.SubjectKind.User, "uid-missing")))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanBindingService.CODE_BINDING_INVALID))
                .verify();
    }

    @Test
    void rejectsAnEmptyRequest() {
        StepVerifier.create(service.bulk(new PlanBindingService.BulkBindRequest(
                        "standard", List.of(), 0, true)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanBindingService.CODE_BINDING_INVALID))
                .verify();
    }

    @Test
    void appliesEffectiveWindowToBulkBindings() {
        when(userGroupService.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));
        var from = PlanTestFixtures.NOW.plusSeconds(3600);
        var to = PlanTestFixtures.NOW.plusSeconds(7200);
        var subject = new PlanBindingService.BindingSubject(AiPlanBinding.SubjectKind.UserGroup, "rd");

        StepVerifier.create(service.bulk(new PlanBindingService.BulkBindRequest(
                        "standard", List.of(subject), 0, true, from, to)))
                .assertNext(result -> assertThat(result.bindings().getFirst().effectiveFrom())
                        .isEqualTo(from))
                .verifyComplete();

        var created = ArgumentCaptor.forClass(AiPlanBinding.class);
        verify(client).create(created.capture());
        assertThat(created.getValue().getSpec().getEffectiveFrom()).isEqualTo(from);
        assertThat(created.getValue().getSpec().getEffectiveTo()).isEqualTo(to);
    }

    @Test
    void rejectsAnInvalidEffectiveWindow() {
        var subject = new PlanBindingService.BindingSubject(AiPlanBinding.SubjectKind.UserGroup, "rd");
        Instant from = PlanTestFixtures.NOW;

        StepVerifier.create(service.bulk(new PlanBindingService.BulkBindRequest(
                        "standard", List.of(subject), 0, true, from, from)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanBindingService.CODE_BINDING_INVALID))
                .verify();
    }

    @Test
    void deduplicatesRepeatedSubjects() {
        when(userGroupService.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));
        var subject = new PlanBindingService.BindingSubject(AiPlanBinding.SubjectKind.UserGroup, "rd");

        StepVerifier.create(service.bulk(request(List.of(subject, subject))))
                .assertNext(result -> assertThat(result.bindings()).hasSize(1))
                .verifyComplete();

        verify(client).create(any(AiPlanBinding.class));
    }

    private static PlanBindingService.BulkBindRequest request(
            List<PlanBindingService.BindingSubject> subjects) {
        return new PlanBindingService.BulkBindRequest("standard", subjects, 3, true);
    }
}