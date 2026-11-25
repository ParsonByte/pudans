package cn.tolim.pudans.core.ai.embedding;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.core.ai.AiModelDirectory;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
import io.agentscope.core.model.ExecutionConfig;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryEmbeddingModels {

    private static final String API_KEY = "apiKey";

    private final AiModelDirectory models;

    private final AiChannelModelRepository channelModels;

    private final AiChannels channels;

    private final AiProviders providers;

    private final MemoryVectorSpaceRepository spaces;

    public Mono<Space> defaultSpace(String workspaceId) {
        return models
                .resolveDefault(workspaceId, "embedding")
                .flatMap(model -> spaces
                        .findActiveByModelId(model.id())
                        .next()
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "memory-vector-space-not-configured",
                                "Embedding 模型尚未配置可用的向量空间。")))
                        .flatMap(space -> embeddingModel(workspaceId, model, space.getDimension())
                                .map(embeddingModel -> new Space(space, embeddingModel, model))));
    }

    private Mono<EmbeddingModel> embeddingModel(
            String workspaceId, AiModel model, int dimensions) {
        return channelModels
                .findEnabledRoute(model.id(), workspaceId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "memory-embedding-channel-unavailable",
                        "Embedding 模型没有启用的渠道。")))
                .flatMap(channelModel -> channels
                        .get(workspaceId, channelModel.channelId())
                        .flatMap(channel -> channels
                                .credentials(channel.id())
                                .map(credentials -> build(
                                        channelModel.upstreamModelKey(),
                                        channel.protocol(),
                                        channel.provider(),
                                        channel.baseUrl(),
                                        channel.timeoutMs(),
                                        credentials,
                                        dimensions))));
    }

    private EmbeddingModel build(
            String upstreamModel,
            String protocol,
            String provider,
            String baseUrl,
            Integer timeoutMs,
            Map<String, String> credentials,
            int dimensions) {
        if (!supported(protocol, provider)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "memory-embedding-protocol-unsupported",
                    "Embedding 渠道必须使用 OpenAI 兼容协议。");
        }
        var apiKey = credentials.get(API_KEY);
        if (!StringUtils.hasText(apiKey)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "memory-embedding-credential-missing",
                    "Embedding 渠道缺少 apiKey 凭据。");
        }
        var key = apiKey.trim();
        var name = upstreamModel.trim();
        var config = executionConfig(timeoutMs);
        return OpenAITextEmbedding.builder()
                .apiKey(key)
                .modelName(name)
                .dimensions(dimensions)
                .executionConfig(config)
                .baseUrl(embeddingBaseUrl(provider, baseUrl))
                .build();
    }

    private static ExecutionConfig executionConfig(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return null;
        }
        return ExecutionConfig.builder()
                .timeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    private boolean supported(String protocol, String provider) {
        return openaiCompatible(protocol)
                || openaiCompatible(provider)
                || providers.find(provider)
                        .map(AiProvider::supportsOpenAiCompatibleEmbedding)
                        .orElse(false);
    }

    private String embeddingBaseUrl(String provider, String baseUrl) {
        return providers.find(provider)
                .map(value -> value.embeddingBaseUrl(baseUrl))
                .orElseGet(() -> baseUrl.trim());
    }

    private static boolean openaiCompatible(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return "openai".equals(normalized) || "openai-compatible".equals(normalized);
    }

    public record Space(
            MemoryVectorSpaceEntity vectorSpace, EmbeddingModel embeddingModel, AiModel model) {}
}
