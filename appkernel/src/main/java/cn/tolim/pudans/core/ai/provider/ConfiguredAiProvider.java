package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

public class ConfiguredAiProvider implements AiProvider {

    private final String provider;

    private final AiProviderHttp http;

    public ConfiguredAiProvider(String provider, AiProviderHttp http) {
        this.provider = provider;
        this.http = http;
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return new AiProviderDescriptor(
                provider,
                provider,
                "按渠道配置访问的 AI 供应商",
                null,
                null,
                "",
                "chat-completions",
                List.of("chat-completions", "messages", "responses", "systemone"),
                List.of("chat", "embedding", "image", "rerank", "video", "audio", "3d"),
                List.of(provider),
                true,
                List.of());
    }

    @Override
    public Mono<List<AiProviderModel>> discover(
            AiChannelEntity channel, Map<String, String> credentials) {
        var baseUrl = resolveBaseUrl(channel);
        if (!StringUtils.hasText(baseUrl)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-channel-base-url-missing",
                    "AI 渠道缺少基础地址。"));
        }
        return http.getJson(
                        URI.create(baseUrl + "/models"),
                        credentials,
                        channel.getTimeoutMs() == null || channel.getTimeoutMs() <= 0
                                ? http.responseTimeout()
                                : java.time.Duration.ofMillis(channel.getTimeoutMs()))
                .map(ConfiguredAiProvider::models);
    }

    private static List<AiProviderModel> models(String body) {
        try {
            var data = JsonUtils.mapper().readTree(body).path("data");
            var models = new ArrayList<AiProviderModel>();
            for (var item : data) {
                var id = item.path("id").asText("");
                if (StringUtils.hasText(id)) {
                    models.add(new AiProviderModel(id, id, modelType(id), "provider"));
                }
            }
            return List.copyOf(models);
        } catch (Exception error) {
            throw AiProviderHttp.invalidResponse(error);
        }
    }

    private static AiModelType modelType(String modelId) {
        var value = modelId.toLowerCase(Locale.ROOT);
        if (value.contains("embedding")) {
            return AiModelType.EMBEDDING;
        }
        if (value.contains("rerank")) {
            return AiModelType.RERANK;
        }
        if (value.contains("video")) {
            return AiModelType.VIDEO;
        }
        if (value.contains("audio") || value.contains("tts") || value.contains("whisper")) {
            return AiModelType.AUDIO;
        }
        if (value.contains("image") || value.contains("dall-e") || value.contains("flux")) {
            return AiModelType.IMAGE;
        }
        return AiModelType.CHAT;
    }
}