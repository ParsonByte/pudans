package cn.tolim.pudans.core.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.model.AiModelAdapter;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.routing.AiRouteResolutionService;
import cn.tolim.pudans.core.ai.routing.RouteOutcome;
import cn.tolim.pudans.core.ai.routing.RoutePlan;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.core.ai.routing.RouteTrace;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.core.ai.wallet.AiWalletService.FreezeResult;
import cn.tolim.pudans.core.ai.wallet.AiWalletService.ReleaseResult;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelRequestRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGenerationServiceTest {

    private static final String USER = "user-1";

    private static final String IDEMPOTENCY_KEY = "idem-1";

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiProviderAccountService accountService;

    @Mock
    AiRouteResolutionService routeResolutionService;

    @Mock
    AiModelAdapterRegistry adapterRegistry;

    @Mock
    AiModelRequestRepository requestRepository;

    @Mock
    AiWalletService walletService;

    @Mock
    AiGenerationRecorder recorder;

    @Mock
    AiModelTaskScheduler taskScheduler;

    @Mock
    AiModelAdapter adapter;

    @InjectMocks
    AiGenerationService service;

    @Test
    void generatesWithExplicitModelAndRecordsSuccess() {
        var model = model("model-a", "provider-a", AiModel.ModelType.IMAGE, 0);
        var provider = provider("provider-a", AiProvider.Protocol.OPENAI_CHAT);
        var account = account("account-a", "provider-a");
        var row = row(AiModel.Capability.IMAGE_GENERATION, "IMAGE", "provider-a", "account-a", "model-a");
        var response = response(AiModel.Capability.IMAGE_GENERATION, AiModelResponse.TaskStatus.SUCCEEDED);
        stubExplicitTarget(model, provider, account);
        when(adapterRegistry.supports(AiProvider.Protocol.OPENAI_CHAT, AiModel.Capability.IMAGE_GENERATION))
                .thenReturn(true);
        when(adapterRegistry.require(AiProvider.Protocol.OPENAI_CHAT, AiModel.Capability.IMAGE_GENERATION))
                .thenReturn(adapter);
        when(requestRepository.create(any())).thenReturn(Mono.just(row));
        when(adapter.invoke(any(), any(), any(), any())).thenReturn(Mono.just(response));
        when(recorder.succeed(any(), any(), eq(false), any(), any(), any()))
                .thenReturn(Mono.just(result("SUCCEEDED", null)));

        StepVerifier.create(service.generate(command(
                        "model-a", AiModel.Capability.IMAGE_GENERATION)))
                .assertNext(value -> assertThat(value.status()).isEqualTo("SUCCEEDED"))
                .verifyComplete();

        verify(walletService, never()).freeze(anyString(), anyString(), any(), any());
        verify(recorder).succeed(eq(row), eq(model), eq(false), eq(response), any(), any());
    }

    @Test
    void marksPendingProviderTasksAndSchedulesPolling() {
        var model = model("model-v", "provider-v", AiModel.ModelType.VIDEO, 0);
        var provider = provider("provider-v", AiProvider.Protocol.ANTHROPIC);
        var account = account("account-v", "provider-v");
        var row = row(AiModel.Capability.VIDEO_GENERATION, "VIDEO", "provider-v", "account-v", "model-v");
        var updated = rowWithTask("task-1");
        var response = response(AiModel.Capability.VIDEO_GENERATION, AiModelResponse.TaskStatus.PENDING);
        stubExplicitTarget(model, provider, account);
        when(adapterRegistry.supports(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.VIDEO_GENERATION))
                .thenReturn(true);
        when(adapterRegistry.require(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.VIDEO_GENERATION))
                .thenReturn(adapter);
        when(requestRepository.create(any())).thenReturn(Mono.just(row));
        when(adapter.invoke(any(), any(), any(), any())).thenReturn(Mono.just(response));
        when(requestRepository.markTask(anyString(), eq("task-1"))).thenReturn(Mono.just(updated));
        when(taskScheduler.enqueuePoll(updated)).thenReturn(Mono.just(true));

        StepVerifier.create(service.generate(command(
                        "model-v", AiModel.Capability.VIDEO_GENERATION)))
                .assertNext(value -> assertThat(value.providerTaskId()).isEqualTo("task-1"))
                .verifyComplete();

        verify(requestRepository).markTask(anyString(), eq("task-1"));
        verify(taskScheduler).enqueuePoll(updated);
        verify(recorder, never()).succeed(any(), any(), eq(false), any(), any(), any());
    }

    @Test
    void fallsBackToNextRoutedCandidate() {
        var primaryModel = model("model-a", "provider-a", AiModel.ModelType.IMAGE, 0);
        var fallbackModel = model("model-b", "provider-b", AiModel.ModelType.IMAGE, 0);
        var primaryProvider = provider("provider-a", AiProvider.Protocol.OPENAI_CHAT);
        var fallbackProvider = provider("provider-b", AiProvider.Protocol.ANTHROPIC);
        var primarySelection = new RouteSelection(
                "provider-a", "account-a", "model-a", AiModel.Tier.HIGH);
        var fallbackSelection = new RouteSelection(
                "provider-b", "account-b", "model-b", AiModel.Tier.MEDIUM);
        var trace = new RouteTrace(
                AiRouteGroup.RouteMode.SMART,
                null,
                AiModel.Tier.HIGH,
                AiProvider.Affinity.AUTO,
                null,
                List.of(),
                primarySelection,
                RouteOutcome.SELECTED,
                List.of());
        var plan = new RoutePlan(primarySelection, List.of(fallbackSelection), List.of(), trace);
        var routeGroup = routeGroup("default");
        when(requestRepository.findByUserAndIdempotency(USER, IDEMPOTENCY_KEY))
                .thenReturn(Mono.empty());
        when(routeResolutionService.resolveCapability(any()))
                .thenReturn(Mono.just(new AiRouteResolutionService.Resolution(
                        routeGroup, null, null, plan, List.of())));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(primaryProvider));
        when(client.get(AiProviderAccount.class, "account-a"))
                .thenReturn(Mono.just(account("account-a", "provider-a")));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(primaryModel));
        when(client.get(AiProvider.class, "provider-b")).thenReturn(Mono.just(fallbackProvider));
        when(client.get(AiProviderAccount.class, "account-b"))
                .thenReturn(Mono.just(account("account-b", "provider-b")));
        when(client.get(AiModel.class, "model-b")).thenReturn(Mono.just(fallbackModel));
        var fallbackAdapter = org.mockito.Mockito.mock(AiModelAdapter.class);
        when(adapterRegistry.supports(AiProvider.Protocol.OPENAI_CHAT, AiModel.Capability.IMAGE_GENERATION))
                .thenReturn(true);
        when(adapterRegistry.supports(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.IMAGE_GENERATION))
                .thenReturn(true);
        when(adapterRegistry.require(AiProvider.Protocol.OPENAI_CHAT, AiModel.Capability.IMAGE_GENERATION))
                .thenReturn(adapter);
        when(adapterRegistry.require(AiProvider.Protocol.ANTHROPIC, AiModel.Capability.IMAGE_GENERATION))
                .thenReturn(fallbackAdapter);
        var row = row(AiModel.Capability.IMAGE_GENERATION, "IMAGE", "provider-a", "account-a", "model-a");
        var response = response(AiModel.Capability.IMAGE_GENERATION, AiModelResponse.TaskStatus.SUCCEEDED);
        when(requestRepository.create(any())).thenReturn(Mono.just(row));
        when(adapter.invoke(any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("primary down")));
        when(fallbackAdapter.invoke(any(), any(), any(), any())).thenReturn(Mono.just(response));
        when(recorder.succeed(any(), any(), eq(false), any(), any(), any()))
                .thenReturn(Mono.just(result("SUCCEEDED", null)));

        StepVerifier.create(service.generate(command(null, AiModel.Capability.IMAGE_GENERATION)))
                .assertNext(value -> assertThat(value.status()).isEqualTo("SUCCEEDED"))
                .verifyComplete();

        verify(adapter).invoke(any(), any(), any(), any());
        verify(fallbackAdapter).invoke(any(), any(), any(), any());
        verify(recorder).succeed(eq(row), eq(fallbackModel), eq(false), eq(response), any(), any());
    }

    @Test
    void releasesTheHoldWhenTheRequestCannotBeCreated() {
        var model = model("model-a", "provider-a", AiModel.ModelType.IMAGE, 2);
        var provider = provider("provider-a", AiProvider.Protocol.OPENAI_CHAT);
        var account = account("account-a", "provider-a");
        stubExplicitTarget(model, provider, account);
        when(walletService.freeze(anyString(), eq(USER), eq(new BigDecimal("2")), any()))
                .thenReturn(Mono.just(new FreezeResult(
                        "gen-1", USER, new BigDecimal("2"), BigDecimal.TEN, BigDecimal.ZERO, false)));
        when(requestRepository.create(any())).thenReturn(Mono.error(new IllegalStateException("db down")));
        when(walletService.release(anyString()))
                .thenReturn(Mono.just(new ReleaseResult("gen-1", true, "release-gen-1")));

        StepVerifier.create(service.generate(command(
                        "model-a", AiModel.Capability.IMAGE_GENERATION)))
                .expectError(IllegalStateException.class)
                .verify();

        verify(walletService).release(anyString());
        verify(recorder, never()).fail(any(), any(), any());
    }

    private void stubExplicitTarget(
            AiModel model, AiProvider provider, AiProviderAccount account) {
        when(requestRepository.findByUserAndIdempotency(USER, IDEMPOTENCY_KEY))
                .thenReturn(Mono.empty());
        when(client.get(AiModel.class, model.getMetadata().getName())).thenReturn(Mono.just(model));
        when(client.get(AiProvider.class, model.getSpec().getProvider())).thenReturn(Mono.just(provider));
        when(accountService.firstEnabled(model.getSpec().getProvider())).thenReturn(Mono.just(account));
    }

    private static AiGenerationCommand command(
            String model, AiModel.Capability capability) {
        return new AiGenerationCommand(
                USER,
                model,
                "default",
                capability,
                List.of(new AiModelContent.Text("生成一张图片")),
                Map.of(),
                IDEMPOTENCY_KEY,
                null,
                null,
                null);
    }

    private static AiModel model(
            String name, String provider, AiModel.ModelType type, int imagePoints) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        model.getSpec().setProvider(provider);
        model.getSpec().setModelCode(name + "-code");
        model.getSpec().setModelType(type);
        model.getSpec().setImagePointsPerItem(imagePoints);
        return model;
    }

    private static AiProvider provider(String name, AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setEnabled(true);
        return provider;
    }

    private static AiProviderAccount account(String name, String provider) {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName(name);
        account.setMetadata(metadata);
        account.getSpec().setProvider(provider);
        account.getSpec().setEnabled(true);
        return account;
    }

    private static AiRouteGroup routeGroup(String name) {
        var group = new AiRouteGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        return group;
    }

    private static AiModelResponse response(
            AiModel.Capability capability, AiModelResponse.TaskStatus status) {
        var pending = status == AiModelResponse.TaskStatus.PENDING;
        return new AiModelResponse(
                pending ? null : "upstream-1",
                "model-code",
                pending ? List.of() : List.of(new AiModelContent.Image("https://img", null, null, null, null)),
                pending ? null : AiModelUsage.EMPTY,
                null,
                pending ? "task-1" : null,
                status);
    }

    private static ModelRequestRow row(
            AiModel.Capability capability,
            String modelType,
            String provider,
            String account,
            String model) {
        return new ModelRequestRow(
                1L,
                "gen-1",
                "default",
                USER,
                null,
                null,
                null,
                IDEMPOTENCY_KEY,
                "default",
                modelType,
                capability.name(),
                provider,
                account,
                model,
                "HIGH",
                "RUNNING",
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
                null,
                null,
                null,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null);
    }

    private static ModelRequestRow rowWithTask(String providerTaskId) {
        return new ModelRequestRow(
                1L,
                "gen-1",
                "default",
                USER,
                null,
                null,
                null,
                IDEMPOTENCY_KEY,
                "default",
                "VIDEO",
                AiModel.Capability.VIDEO_GENERATION.name(),
                "provider-v",
                "account-v",
                "model-v",
                "HIGH",
                "RUNNING",
                null,
                null,
                providerTaskId,
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
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null);
    }

    private static AiGenerationResult result(String status, String providerTaskId) {
        return new AiGenerationResult(
                "gen-1",
                status,
                "IMAGE",
                AiModel.Capability.IMAGE_GENERATION.name(),
                "provider-a",
                "account-a",
                "model-a",
                List.of(),
                AiModelUsage.EMPTY,
                null,
                null,
                "NONE",
                providerTaskId,
                null,
                null,
                null,
                Instant.now(),
                null);
    }
}
