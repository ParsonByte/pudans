package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiDiscoveredModel;
import cn.tolim.pudans.contract.ai.dto.AiModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelDiscovery;
import cn.tolim.pudans.contract.ai.dto.AiModelImportRequest;
import cn.tolim.pudans.core.ai.AiChannelModels;
import cn.tolim.pudans.core.ai.AiModels;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiModelImportsTest {

    @Mock
    AiModelDiscoveries discoveries;

    @Mock
    AiModels models;

    @Mock
    AiChannelModels channelModels;

    @Mock
    AiModelRepository modelRepository;

    @Mock
    AiChannelModelRepository channelModelRepository;

    @Mock
    TransactionalOperator transactions;

    @Test
    void importsASelectedModelAndBindsTheChannelRoute() {
        var channelId = UUID.randomUUID();
        var saved = model(channelId);
        var route = route(channelId, saved);
        when(discoveries.discover("workspace-a", channelId)).thenReturn(Mono.just(discovery(channelId)));
        when(modelRepository.findAll()).thenReturn(Flux.empty());
        when(channelModelRepository.findByChannelId(channelId)).thenReturn(Flux.empty());
        when(models.create(any(AiModelCreateRequest.class))).thenReturn(Mono.just(saved));
        when(channelModels.create(any(AiChannelModelCreateRequest.class))).thenReturn(Mono.just(route));
        doAnswer(invocation -> invocation.getArgument(0))
                .when(transactions)
                .transactional(org.mockito.ArgumentMatchers.<Mono<Object>>any());

        var imports = new AiModelImports(
                discoveries, models, channelModels, modelRepository, channelModelRepository, transactions);

        StepVerifier.create(imports.importModels(
                        "workspace-a",
                        channelId,
                        new AiModelImportRequest(List.of("custom-model"))))
                .assertNext(result -> {
                    assertThat(result.created()).isEqualTo(1);
                    assertThat(result.updated()).isZero();
                    assertThat(result.skipped()).isZero();
                })
                .verifyComplete();

        var request = ArgumentCaptor.forClass(AiModelCreateRequest.class);
        verify(models).create(request.capture());
        assertThat(request.getValue().logicalName()).isEqualTo("custom/custom-model");
        assertThat(request.getValue().contextWindow()).isEqualTo(1_048_576);
        assertThat(request.getValue().capabilities()).containsKey("chat");
        assertThat(details(request.getValue().capabilities(), "chat"))
                .containsEntry(
                        "features",
                        List.of("streaming", "tools", "reasoning", "structured_output"))
                .containsEntry(
                        "modalities",
                        Map.of("input", List.of("text"), "output", List.of("text")));
        verify(channelModels).create(any(AiChannelModelCreateRequest.class));
    }

    @Test
    void skipsModelsOutsideTheRequestedSelection() {
        var channelId = UUID.randomUUID();
        when(discoveries.discover("workspace-a", channelId)).thenReturn(Mono.just(discovery(channelId)));
        when(modelRepository.findAll()).thenReturn(Flux.empty());
        when(channelModelRepository.findByChannelId(channelId)).thenReturn(Flux.empty());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(transactions)
                .transactional(org.mockito.ArgumentMatchers.<Mono<Object>>any());

        var imports = new AiModelImports(
                discoveries, models, channelModels, modelRepository, channelModelRepository, transactions);

        StepVerifier.create(imports.importModels(
                        "workspace-a",
                        channelId,
                        new AiModelImportRequest(List.of("unknown-model"))))
                .assertNext(result -> {
                    assertThat(result.created()).isZero();
                    assertThat(result.skipped()).isEqualTo(2);
                })
                .verifyComplete();
    }

    private static AiModelDiscovery discovery(UUID channelId) {
        var model = new AiDiscoveredModel(
                "custom-model",
                "Custom Model",
                "Custom snapshot",
                "chat",
                "chat-completions",
                List.of("chat-completions", "messages", "responses"),
                1_048_576,
                393_216,
                List.of("chat"),
                List.of("streaming", "tools", "reasoning", "structured_output"),
                Map.of("input", List.of("text"), "output", List.of("text")),
                Map.of("protocol", "chat-completions"),
                Map.of("provider", "custom", "model", "custom-model"),
                "provider",
                null,
                null);
        return new AiModelDiscovery(
                channelId,
                "workspace-a",
                "custom",
                "Custom",
                "chat-completions",
                "https://api.custom.com/v1",
                "custom",
                Instant.parse("2026-11-25T00:00:00Z"),
                Instant.parse("2026-11-25T00:00:00Z"),
                List.of(model));
    }

    private static AiModel model(UUID channelId) {
        return new AiModel(
                UUID.randomUUID(),
                "custom/custom-model",
                "Custom Model",
                null,
                Map.of("chat", Map.of("type", "chat")),
                1_048_576,
                393_216,
                Map.of("protocol", "chat-completions"),
                "active",
                Instant.now(),
                Instant.now());
    }

    private static AiChannelModel route(UUID channelId, AiModel model) {
        return new AiChannelModel(
                UUID.randomUUID(),
                channelId,
                model.id(),
                "custom-model",
                100,
                100,
                true,
                Map.of(),
                Map.of("upstreamModelKey", "custom-model"),
                Instant.now(),
                Instant.now());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Map<String, Object> capabilities, String key) {
        return (Map<String, Object>) capabilities.get(key);
    }
}
