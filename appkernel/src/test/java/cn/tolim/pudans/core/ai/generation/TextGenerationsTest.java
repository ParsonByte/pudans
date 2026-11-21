package cn.tolim.pudans.core.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.RouteGroups;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChat;
import cn.tolim.pudans.core.ai.routing.RouteCandidates;
import cn.tolim.pudans.core.ai.routing.RouteEngine;
import cn.tolim.pudans.core.ai.routing.RouteOutcome;
import cn.tolim.pudans.core.ai.routing.RoutePlan;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.RouteTrace;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TextGenerationsTest {

    @Mock
    RouteGroups routeGroups;

    @Mock
    RouteCandidates routeCandidates;

    @Mock
    RouteEngine routeEngine;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LangChain4jChat langChain4jChat;

    private TextGenerations service;

    @BeforeEach
    void setUp() {
        service = new TextGenerations(
                routeGroups, routeCandidates, routeEngine, langChain4jChat, client, new AiProperties());
    }

    @Test
    void generatesContentThroughTheExplicitRouteGroup() {
        var group = group("group-a");
        var seat = seat("provider-a", "account-a", "model-a");
        var selection = RouteSelection.of(seat);
        when(routeGroups.get("group-a")).thenReturn(Mono.just(group));
        when(routeCandidates.expand(group)).thenReturn(Mono.just(List.of(seat)));
        when(routeEngine.route(any(), anyList())).thenReturn(plan(selection, List.of()));
        stubResources("provider-a", "account-a", "model-a", "gpt-x");
        when(langChain4jChat.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any())).thenReturn(Mono.just(response("hello")));

        StepVerifier.create(service.generate(new GenerationRequest("group-a", "system", "user", 0, 0.2)))
                .assertNext(result -> {
                    assertThat(result.content()).isEqualTo("hello");
                    assertThat(result.modelCode()).isEqualTo("gpt-x");
                    assertThat(result.provider()).isEqualTo("provider-a");
                    assertThat(result.account()).isEqualTo("account-a");
                })
                .verifyComplete();

        var irCaptor = ArgumentCaptor.forClass(AiChatIr.class);
        verify(langChain4jChat).chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), irCaptor.capture());
        var ir = irCaptor.getValue();
        assertThat(ir.stream()).isFalse();
        assertThat(ir.requestParams().maxTokens()).isEqualTo(1024);
        assertThat(ir.requestParams().temperature()).isEqualTo(0.2);
        assertThat(ir.messages()).extracting(AiChatMessage::role).containsExactly("system", "user");

        var requestCaptor = ArgumentCaptor.forClass(RouteRequest.class);
        verify(routeEngine).route(requestCaptor.capture(), anyList());
        var routeRequest = requestCaptor.getValue();
        assertThat(routeRequest.isClassifierEnabled()).isFalse();
        assertThat(routeRequest.getQualityThreshold()).isEqualTo(0.6);
        assertThat(routeRequest.getHealthThreshold()).isEqualTo(60);
        assertThat(routeRequest.getRequestedTier()).isNull();
        assertThat(routeRequest.getMaxOutputTokens()).isEqualTo(1024);
    }

    @Test
    void fallsBackToTheNextSeatWhenTheAdapterFails() {
        var group = group("group-a");
        var first = seat("provider-a", "account-a", "model-a");
        var second = seat("provider-b", "account-b", "model-b");
        var firstSelection = RouteSelection.of(first);
        when(routeGroups.get("group-a")).thenReturn(Mono.just(group));
        when(routeCandidates.expand(group)).thenReturn(Mono.just(List.of(first, second)));
        when(routeEngine.route(any(), anyList()))
                .thenReturn(plan(firstSelection, List.of(RouteSelection.of(second))));
        stubResources("provider-a", "account-a", "model-a", "gpt-a");
        stubResources("provider-b", "account-b", "model-b", "gpt-b");
        when(langChain4jChat.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")), Mono.just(response("fallback")));

        StepVerifier.create(service.generate(new GenerationRequest("group-a", "system", "user", 256, 0.1)))
                .assertNext(result -> {
                    assertThat(result.content()).isEqualTo("fallback");
                    assertThat(result.modelCode()).isEqualTo("gpt-b");
                    assertThat(result.provider()).isEqualTo("provider-b");
                    assertThat(result.account()).isEqualTo("account-b");
                })
                .verifyComplete();
    }

    @Test
    void reportsBadGatewayWhenEveryCandidateFails() {
        var group = group("group-a");
        var seat = seat("provider-a", "account-a", "model-a");
        var selection = RouteSelection.of(seat);
        when(routeGroups.get("group-a")).thenReturn(Mono.just(group));
        when(routeCandidates.expand(group)).thenReturn(Mono.just(List.of(seat)));
        when(routeEngine.route(any(), anyList())).thenReturn(plan(selection, List.of()));
        stubResources("provider-a", "account-a", "model-a", "gpt-a");
        when(langChain4jChat.chat(any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(service.generate(new GenerationRequest("group-a", "system", "user", 128, 0.1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode().value()).isEqualTo(502);
                    assertThat(apiException.getCode()).isEqualTo("ai-generation-failed");
                })
                .verify();
    }

    @Test
    void reportsServiceUnavailableWhenTheDefaultGroupIsMissing() {
        when(routeGroups.defaultGroup()).thenReturn(Mono.empty());

        StepVerifier.create(service.generate(new GenerationRequest(null, "system", "user", 128, 0.1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode().value()).isEqualTo(503);
                    assertThat(apiException.getCode()).isEqualTo("ai-route-no-candidate");
                })
                .verify();
    }

    @Test
    void reportsServiceUnavailableWhenRoutingFindsNoCandidate() {
        var group = group("group-a");
        when(routeGroups.get("group-a")).thenReturn(Mono.just(group));
        when(routeCandidates.expand(group)).thenReturn(Mono.just(List.of()));
        when(routeEngine.route(any(), anyList())).thenReturn(plan(null, List.of()));

        StepVerifier.create(service.generate(new GenerationRequest("group-a", "system", "user", 128, 0.1)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode().value()).isEqualTo(503);
                    assertThat(apiException.getCode()).isEqualTo("ai-route-no-candidate");
                })
                .verify();
    }

    private void stubResources(
            String providerName, String accountName, String modelName, String modelCode) {
        when(client.get(AiProvider.class, providerName))
                .thenReturn(Mono.just(provider(providerName)));
        when(client.get(AiProviderAccount.class, accountName))
                .thenReturn(Mono.just(account(accountName)));
        when(client.get(AiModel.class, modelName))
                .thenReturn(Mono.just(model(modelName, modelCode)));
    }

    private static AiRouteGroup group(String name) {
        var group = new AiRouteGroup();
        group.setMetadata(metadata(name));
        group.getSpec().setDisplayName(name);
        return group;
    }

    private static RouteSeat seat(String provider, String account, String model) {
        return RouteSeat.builder()
                .provider(provider)
                .account(account)
                .model(model)
                .tier(AiModel.Tier.CHEAP)
                .enabled(true)
                .build();
    }

    private static RoutePlan plan(RouteSelection selected, List<RouteSelection> fallback) {
        return new RoutePlan(
                selected,
                fallback,
                List.of(),
                new RouteTrace(
                        AiRouteGroup.RouteMode.SMART,
                        null,
                        AiModel.Tier.CHEAP,
                        AiProvider.Affinity.AUTO,
                        null,
                        List.of(),
                        selected,
                        selected == null ? RouteOutcome.EXHAUSTED : RouteOutcome.SELECTED,
                        List.of()));
    }

    private static AiProvider provider(String name) {
        var provider = new AiProvider();
        provider.setMetadata(metadata(name));
        provider.getSpec().setDisplayName(name);
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        return provider;
    }

    private static AiProviderAccount account(String name) {
        var account = new AiProviderAccount();
        account.setMetadata(metadata(name));
        account.getSpec().setDisplayName(name);
        return account;
    }

    private static AiModel model(String name, String modelCode) {
        var model = new AiModel();
        model.setMetadata(metadata(name));
        model.getSpec().setModelCode(modelCode);
        return model;
    }

    private static AiChatResponse response(String content) {
        return new AiChatResponse(
                "response-id",
                "gpt-x",
                new AiChatMessage("assistant", List.of(new AiContentPart.Text(content, null))),
                null,
                "stop");
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
