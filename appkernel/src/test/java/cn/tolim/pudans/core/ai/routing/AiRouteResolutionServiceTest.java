package cn.tolim.pudans.core.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiChatMode;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.config.AiChatModeService;
import cn.tolim.pudans.core.ai.config.AiRouteGroupService;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiRouteResolutionServiceTest {

    @Mock
    AiChatModeService chatModeService;

    @Mock
    AiRouteGroupService routeGroupService;

    @Mock
    AiSettingService settingService;

    @Mock
    RouteCandidateService candidateService;

    @Mock
    RouteEngine routeEngine;

    private AiRouteResolutionService service;

    @BeforeEach
    void setUp() {
        var properties = new AiProperties();
        service = new AiRouteResolutionService(
                chatModeService, routeGroupService, settingService, candidateService, routeEngine, properties);
    }

    @Test
    void resolvesTheExplicitRouteGroupAndMode() {
        var group = group("group-a");
        group.getSpec().setParentMode(AiRouteGroup.ParentMode.PRO);
        var mode = mode("pro", "group-b", AiModel.Tier.HIGH);
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));
        when(chatModeService.get("pro")).thenReturn(Mono.just(mode));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input("pro", "group-a")))
                .assertNext(resolution -> {
                    assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("group-a");
                    assertThat(resolution.chatMode().getMetadata().getName()).isEqualTo("pro");
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(RouteRequest.class);
        org.mockito.Mockito.verify(routeEngine).route(captor.capture(), anyList());
        assertThat(captor.getValue().getChatModeDefaultTier()).isEqualTo(AiModel.Tier.HIGH);
        assertThat(captor.getValue().getRouteGroupDefaultTier()).isEqualTo(AiModel.Tier.CHEAP);
    }

    @Test
    void usesTheModeRouteGroupWhenNoGroupIsGiven() {
        var mode = mode("fast", "fast-group", AiModel.Tier.CHEAP);
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode));
        when(routeGroupService.get("fast-group")).thenReturn(Mono.just(group("fast-group")));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input("fast", null)))
                .assertNext(resolution ->
                        assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("fast-group"))
                .verifyComplete();
    }

    @Test
    void fallsBackToTheDefaultRouteGroup() {
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.defaultGroup()).thenReturn(Mono.just(group("default")));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input(null, null)))
                .assertNext(resolution ->
                        assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("default"))
                .verifyComplete();
    }

    @Test
    void rejectsARouteGroupWhoseParentModeDoesNotMatchTheFixedMode() {
        var group = group("group-a");
        var mode = mode("pro", "group-b", AiModel.Tier.HIGH);
        when(chatModeService.get("pro")).thenReturn(Mono.just(mode));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));

        StepVerifier.create(service.resolve(input("pro", "group-a")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("group-a")
                        .hasMessageContaining("pro"))
                .verify();
    }

    @Test
    void rejectsImplicitSelectionOfANoneGroupInAFixedMode() {
        var mode = mode("fast", "group-b", AiModel.Tier.CHEAP);
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode));
        when(routeGroupService.get("group-b")).thenReturn(Mono.just(group("group-b")));

        StepVerifier.create(service.resolve(input("fast", null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-route-group-mode-mismatch");
                })
                .verify();
    }

    @Test
    void forcesTheBuiltinFastGroupParentModeEvenWhenTheTemplateConfiguresAnotherValue() {
        var mode = mode("fast", "fast-group", AiModel.Tier.CHEAP);
        var builtin = group("fast-group");
        builtin.getSpec().setParentMode(AiRouteGroup.ParentMode.PRO);
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode));
        when(routeGroupService.get("fast-group")).thenReturn(Mono.just(builtin));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input("fast", null)))
                .assertNext(resolution ->
                        assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("fast-group"))
                .verifyComplete();
    }

    @Test
    void acceptsANoneGroupWithAnExplicitSelectionInANonFixedMode() {
        var mode = mode("balanced", null, AiModel.Tier.CHEAP);
        when(chatModeService.get("balanced")).thenReturn(Mono.just(mode));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group("group-a")));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input("balanced", "group-a")))
                .assertNext(resolution ->
                        assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("group-a"))
                .verifyComplete();
    }

    @Test
    void passesTheGroupRulesContextModePatienceAndSinkIntoTheRouteRequest() {
        var group = group("group-a");
        var rule = new AiRouteGroup.Rule();
        rule.setCondition("model:gpt-test");
        rule.setMember("model-a");
        group.getSpec().setContextMode("LARGEST");
        group.getSpec().setPatienceSeconds(30);
        group.getSpec().setFirstTokenSeconds(8);
        group.getSpec().setSink(true);
        group.getSpec().setRules(List.of(rule));
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input(null, "group-a")))
                .expectNextCount(1)
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(RouteRequest.class);
        org.mockito.Mockito.verify(routeEngine).route(captor.capture(), anyList());
        var request = captor.getValue();
        assertThat(request.getContextMode()).isEqualTo("LARGEST");
        assertThat(request.getPatienceSeconds()).isEqualTo(30);
        assertThat(request.getFirstTokenSeconds()).isEqualTo(8);
        assertThat(request.isSink()).isTrue();
        assertThat(request.getRules()).containsExactly(rule);
    }

    @Test
    void rejectsADisabledMode() {
        var mode = mode("fast", "fast-group", AiModel.Tier.CHEAP);
        mode.getSpec().setEnabled(false);
        when(chatModeService.get("fast")).thenReturn(Mono.just(mode));

        StepVerifier.create(service.resolve(input("fast", null)))
                .expectError(ApiException.class)
                .verify();
    }

    @Test
    void rejectsAnEmptyCandidatePlan() {
        var group = group("group-a");
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
        when(routeEngine.route(any(), anyList())).thenReturn(new RoutePlan(
                null,
                List.of(),
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART, null, null, null, null, List.of(), null,
                        RouteOutcome.EXHAUSTED, List.of())));

        StepVerifier.create(service.resolve(input(null, "group-a")))
                .expectError(ApiException.class)
                .verify();
    }

    @Test
    void requiresFileInputWhenTheRequestCarriesADocument() {
        var group = group("group-a");
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());
        var ir = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(
                        new AiContentPart.Text("总结这份文档", null),
                        new AiContentPart.File("application/pdf", "AA==", null, "notes.pdf", null, null)))),
                List.of(),
                new AiRequestParams(null, null, 128, null, null, null, null, null),
                true);

        StepVerifier.create(service.resolve(new AiRouteResolutionService.Input(
                        null, "group-a", null, ir, new AiSetting.Spec(), null, null)))
                .expectNextCount(1)
                .verifyComplete();

        org.mockito.Mockito.verify(candidateService)
                .expand(
                        group,
                        Set.of(AiModel.Capability.CHAT),
                        Set.of(AiModel.Modality.TEXT, AiModel.Modality.FILE),
                        Set.of(AiModel.Modality.TEXT));
    }

    @Test
    void fallsBackToTheReferencedGroupWhenThePrimaryGroupHasNoCandidate() {
        var primary = group("group-a");
        primary.getSpec().setFallback(List.of("group-b"));
        var fallback = group("group-b");
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(primary));
        when(routeGroupService.get("group-b")).thenReturn(Mono.just(fallback));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(exhaustedPlan(), plan());

        StepVerifier.create(service.resolve(input(null, "group-a")))
                .assertNext(resolution -> {
                    assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("group-b");
                    assertThat(resolution.selection()).isNotNull();
                    assertThat(resolution.plan().trace().fallbackGroups())
                            .containsExactly("group-a", "group-b");
                })
                .verifyComplete();
    }

    @Test
    void doesNotLoopWhenFallbackGroupsReferenceEachOther() {
        var primary = group("group-a");
        primary.getSpec().setFallback(List.of("group-b"));
        var fallback = group("group-b");
        fallback.getSpec().setFallback(List.of("group-a"));
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(primary));
        when(routeGroupService.get("group-b")).thenReturn(Mono.just(fallback));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(exhaustedPlan());

        StepVerifier.create(service.resolve(input(null, "group-a")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("没有可用候选"))
                .verify();
    }

    @Test
    void reportsWhyEachCandidateWasEliminated() {
        var group = group("group-a");
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));
        when(candidateService.expand(any(), any(), any(), any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(new RoutePlan(
                null,
                List.of(),
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART,
                        null,
                        null,
                        null,
                        null,
                        List.of(new RouteTrace.Candidate(
                                "provider-a",
                                "account-a",
                                "model-a",
                                AiModel.Tier.CHEAP,
                                false,
                                RouteEliminationReason.RESTING,
                                50)),
                        null,
                        RouteOutcome.EXHAUSTED,
                        List.of())));

        StepVerifier.create(service.resolve(input(null, "group-a")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("provider-a/account-a/model-a")
                        .hasMessageContaining("熔断冷却中"))
                .verify();
    }

    private static AiRouteResolutionService.Input input(String mode, String group) {
        var ir = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hi", null)))),
                List.of(),
                new AiRequestParams(null, null, 128, null, null, null, null, null),
                false);
        return new AiRouteResolutionService.Input(mode, group, null, ir, new AiSetting.Spec(), null, null);
    }

    private static AiRouteGroup group(String name) {
        var group = new AiRouteGroup();
        group.setMetadata(metadata(name));
        group.getSpec().setDisplayName(name);
        group.getSpec().setDefaultTier(AiModel.Tier.CHEAP);
        return group;
    }

    private static AiChatMode mode(String name, String group, AiModel.Tier tier) {
        var mode = new AiChatMode();
        mode.setMetadata(metadata(name));
        mode.getSpec().setDisplayName(name);
        mode.getSpec().setRouteGroup(group);
        mode.getSpec().setDefaultTier(tier);
        return mode;
    }

    private static RouteSeat seat() {
        return RouteSeat.builder()
                .provider("provider-a")
                .account("account-a")
                .model("model-a")
                .tier(AiModel.Tier.CHEAP)
                .enabled(true)
                .capabilities(Set.of(AiModel.Capability.CHAT, AiModel.Capability.STREAM))
                .build();
    }

    private static RoutePlan exhaustedPlan() {
        return new RoutePlan(
                null,
                List.of(),
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART,
                        null,
                        AiModel.Tier.CHEAP,
                        null,
                        null,
                        List.of(),
                        null,
                        RouteOutcome.EXHAUSTED,
                        List.of()));
    }

    private static RoutePlan plan() {
        var selection = new RouteSelection("provider-a", "account-a", "model-a", AiModel.Tier.CHEAP);
        return new RoutePlan(
                selection,
                List.of(),
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART, null, AiModel.Tier.CHEAP, null, null, List.of(),
                        selection, RouteOutcome.SELECTED, List.of()));
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
