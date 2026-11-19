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
        var mode = mode("pro", "group-b", AiModel.Tier.HIGH);
        when(routeGroupService.get("group-a")).thenReturn(Mono.just(group));
        when(chatModeService.get("pro")).thenReturn(Mono.just(mode));
        when(candidateService.expand(group)).thenReturn(Mono.just(List.of(seat())));
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
        when(candidateService.expand(any())).thenReturn(Mono.just(List.of(seat())));
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
        when(candidateService.expand(any())).thenReturn(Mono.just(List.of(seat())));
        when(routeEngine.route(any(), anyList())).thenReturn(plan());

        StepVerifier.create(service.resolve(input(null, null)))
                .assertNext(resolution ->
                        assertThat(resolution.routeGroup().getMetadata().getName()).isEqualTo("default"))
                .verifyComplete();
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
        when(candidateService.expand(group)).thenReturn(Mono.just(List.of()));
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

    private static AiRouteResolutionService.Input input(String mode, String group) {
        var ir = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hi", null)))),
                List.of(),
                new AiRequestParams(null, null, 128, null, null, null, null, null),
                false);
        return new AiRouteResolutionService.Input(mode, group, null, ir, new AiSetting.Spec(), null);
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
