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
                        new AiModelImportRequest(List.of("deepseek-v4-pro"))))
                .assertNext(result -> {
                    assertThat(result.created()).isEqualTo(1);
                    assertThat(result.updated()).isZero();
                    assertThat(result.skipped()).isZero();
                })
                .verifyComplete();

        var request = ArgumentCaptor.forClass(AiModelCreateRequest.class);
        verify(models).create(request.capture());
        assertThat(request.getValue().logicalName()).isEqualTo("deepseek/deepseek-v4-pro");
        assertThat(request.getValue().contextWindow()).isEqualTo(1_048_576);
        assertThat(request.getValue().capabilities()).containsKey("chat");
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
                "deepseek-v4-pro",
                "DeepSeek V4 Pro",
                "DeepSeek snapshot",
                "chat",
                "chat-completions",
                List.of("chat-completions", "messages", "responses"),
                1_048_576,
                393_216,
                List.of("chat"),
                List.of("streaming", "tools", "reasoning", "structured_output"),
                Map.of("input", List.of("text"), "output", List.of("text")),
                Map.of("protocol", "chat-completions"),
                Map.of("provider", "deepseek", "model", "deepseek-v4-pro"),
                "provider",
                null,
                null);
        return new AiModelDiscovery(
                channelId,
                "workspace-a",
                "deepseek",
                "DeepSeek",
                "chat-completions",
                "https://api.deepseek.com/v1",
                "deepseek",
                Instant.parse("2026-11-25T00:00:00Z"),
                Instant.parse("2026-11-25T00:00:00Z"),
                List.of(model));
    }

    private static AiModel model(UUID channelId) {
        return new AiModel(
                UUID.randomUUID(),
                "deepseek/deepseek-v4-pro",
                "DeepSeek V4 Pro",
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
                "deepseek-v4-pro",
                100,
                100,
                true,
                Map.of(),
                Map.of("upstreamModelKey", "deepseek-v4-pro"),
                Instant.now(),
                Instant.now());
    }
}
