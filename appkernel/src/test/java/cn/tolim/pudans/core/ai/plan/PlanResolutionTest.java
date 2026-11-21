package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiPlanBinding;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PlanResolutionTest {

    private static final String UID = "uid-alice";

    private static final String USERNAME = "alice";

    @Mock
    Accounts accounts;

    @Mock
    Groups userGroupService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiSettings aiSettings;

    private PlanResolution service;

    @BeforeEach
    void setUp() {
        service = new PlanResolution(
                accounts, userGroupService, client, aiSettings, new PlanCatalog(client));
        service.setClock(Clock.fixed(PlanTestFixtures.NOW, ZoneOffset.UTC));
        lenient()
                .when(aiSettings.spec())
                .thenReturn(Mono.just(PlanTestFixtures.setting(
                        "default-plan", AiSetting.UnassignedPolicy.DEFAULT_PLAN)));
        lenient().when(accounts.getUserByUid(UID)).thenReturn(Mono.just(user()));
        lenient().when(userGroupService.listMembershipBindings(USERNAME)).thenReturn(Flux.empty());
        lenient()
                .when(client.listAll(eq(AiPlanBinding.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.empty());
        lenient()
                .when(client.get(eq(AiPlan.class), anyString()))
                .thenAnswer(invocation -> Mono.just(PlanTestFixtures.plan(invocation.getArgument(1))));
    }

    @Test
    void prefersDirectUserBindingsOverGroupBindings() {
        stubGroups("group-a");
        stubBindings(
                PlanTestFixtures.binding(
                        "binding-group", AiPlanBinding.SubjectKind.UserGroup, "group-a", "group-plan", 9),
                PlanTestFixtures.binding(
                        "binding-user", AiPlanBinding.SubjectKind.User, UID, "user-plan", 0));

        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> {
                    assertThat(resolved.planName()).isEqualTo("user-plan");
                    assertThat(resolved.matchedBy()).isEqualTo("User/" + UID);
                })
                .verifyComplete();
    }

    @Test
    void selectsTheHighestPriorityGroupBinding() {
        stubGroups("group-a", "group-b");
        stubBindings(
                PlanTestFixtures.binding(
                        "binding-a", AiPlanBinding.SubjectKind.UserGroup, "group-a", "plan-a", 1),
                PlanTestFixtures.binding(
                        "binding-b", AiPlanBinding.SubjectKind.UserGroup, "group-b", "plan-b", 5));

        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> {
                    assertThat(resolved.planName()).isEqualTo("plan-b");
                    assertThat(resolved.matchedBy()).isEqualTo("UserGroup/group-b");
                })
                .verifyComplete();
    }

    @Test
    void breaksGroupPriorityTiesByBindingName() {
        stubGroups("group-a", "group-b");
        stubBindings(
                PlanTestFixtures.binding(
                        "binding-b", AiPlanBinding.SubjectKind.UserGroup, "group-b", "plan-b", 5),
                PlanTestFixtures.binding(
                        "binding-a", AiPlanBinding.SubjectKind.UserGroup, "group-a", "plan-a", 5));

        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> {
                    assertThat(resolved.planName()).isEqualTo("plan-a");
                    assertThat(resolved.matchedBy()).isEqualTo("UserGroup/group-a");
                })
                .verifyComplete();
    }

    @Test
    void skipsDisabledPlans() {
        var disabled = PlanTestFixtures.plan("disabled");
        disabled.getSpec().setEnabled(false);
        when(client.get(AiPlan.class, "disabled")).thenReturn(Mono.just(disabled));
        stubGroups("group-a");
        stubBindings(
                PlanTestFixtures.binding(
                        "binding-user", AiPlanBinding.SubjectKind.User, UID, "disabled", 0),
                PlanTestFixtures.binding(
                        "binding-group", AiPlanBinding.SubjectKind.UserGroup, "group-a", "group-plan", 0));

        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> assertThat(resolved.planName()).isEqualTo("group-plan"))
                .verifyComplete();
    }

    @Test
    void ignoresBindingsOutsideTheirEffectiveRange() {
        var expired = PlanTestFixtures.binding(
                "binding-expired", AiPlanBinding.SubjectKind.User, UID, "expired-plan", 0);
        expired.getSpec().setEffectiveTo(PlanTestFixtures.NOW.minusSeconds(60));
        var future = PlanTestFixtures.binding(
                "binding-future", AiPlanBinding.SubjectKind.User, UID, "future-plan", 0);
        future.getSpec().setEffectiveFrom(PlanTestFixtures.NOW.plusSeconds(60));
        stubGroups("group-a");
        stubBindings(
                expired,
                future,
                PlanTestFixtures.binding(
                        "binding-group", AiPlanBinding.SubjectKind.UserGroup, "group-a", "group-plan", 0));

        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> assertThat(resolved.planName()).isEqualTo("group-plan"))
                .verifyComplete();
    }

    @Test
    void fallsBackToTheDefaultPlan() {
        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> {
                    assertThat(resolved.planName()).isEqualTo("default-plan");
                    assertThat(resolved.matchedBy()).isEqualTo("DefaultPlan");
                })
                .verifyComplete();
    }

    @Test
    void rejectsUsersWithoutAPlanWhenThePolicyIsDeny() {
        when(aiSettings.spec())
                .thenReturn(Mono.just(PlanTestFixtures.setting(
                        "default-plan", AiSetting.UnassignedPolicy.DENY)));

        StepVerifier.create(service.resolve(UID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo(PlanResolution.CODE_PLAN_MISSING);
                })
                .verify();
    }

    @Test
    void rejectsUsersWithoutAPlanWhenNoDefaultPlanIsConfigured() {
        when(aiSettings.spec())
                .thenReturn(Mono.just(PlanTestFixtures.setting(
                        null, AiSetting.UnassignedPolicy.DEFAULT_PLAN)));

        StepVerifier.create(service.resolve(UID))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanResolution.CODE_PLAN_MISSING))
                .verify();
    }

    @Test
    void resolvesDirectBindingsWhenTheUserRecordIsMissing() {
        when(accounts.getUserByUid(UID)).thenReturn(Mono.empty());
        stubBindings(PlanTestFixtures.binding(
                "binding-user", AiPlanBinding.SubjectKind.User, UID, "user-plan", 0));

        StepVerifier.create(service.resolve(UID))
                .assertNext(resolved -> assertThat(resolved.planName()).isEqualTo("user-plan"))
                .verifyComplete();
    }

    @Test
    void cachesResolutionsUntilInvalidated() {
        stubDirectBinding();

        service.resolve(UID).block();
        service.resolve(UID).block();
        verify(accounts, times(1)).getUserByUid(UID);

        service.invalidateAll();
        service.resolve(UID).block();
        verify(accounts, times(2)).getUserByUid(UID);
    }

    private void stubDirectBinding() {
        stubBindings(PlanTestFixtures.binding(
                "binding-user", AiPlanBinding.SubjectKind.User, UID, "user-plan", 0));
    }

    private void stubGroups(String... groups) {
        when(userGroupService.listMembershipBindings(USERNAME))
                .thenReturn(Flux.fromArray(groups).map(PlanTestFixtures::membership));
    }

    private void stubBindings(AiPlanBinding... bindings) {
        when(client.listAll(eq(AiPlanBinding.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.fromArray(bindings));
    }

    private static User user() {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(USERNAME);
        user.setMetadata(metadata);
        user.getSpec().setUid(UID);
        return user;
    }
}