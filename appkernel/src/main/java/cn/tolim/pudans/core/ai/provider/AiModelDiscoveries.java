package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.contract.ai.dto.AiDiscoveredModel;
import cn.tolim.pudans.contract.ai.dto.AiModelDiscovery;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.core.ai.gateway.AiChannelSecrets;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiModelDiscoveries {

    private final AiChannelRepository channels;

    private final AiChannelSecrets secrets;

    private final AiProviders providers;

    private final AiModelRepository models;

    private Clock clock = Clock.systemUTC();

    public Mono<AiModelDiscovery> discover(String workspaceId, UUID channelId) {
        var workspace = AiSupport.requiredText(
                workspaceId, "request-workspace-missing", "缺少当前工作区请求头。");
        return channels.findByWorkspaceAndId(workspace, channelId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-channel-not-found", "AI 渠道不存在。")))
                .flatMap(channel -> {
                    var provider = providers.require(channel.getProvider());
                    return Mono.zip(
                                    secrets.resolve(channel)
                                            .flatMap(credentials ->
                                                    provider.discover(channel, credentials)),
                                    models.findAll().collectMap(AiModel::logicalName))
                            .map(tuple -> discovery(
                                    workspace,
                                    channel,
                                    provider,
                                    tuple.getT1(),
                                    tuple.getT2()));
                });
    }

    private AiModelDiscovery discovery(
            String workspace,
            AiChannelEntity channel,
            AiProvider provider,
            List<AiProviderModel> discovered,
            Map<String, AiModel> existing) {
        var discoveredAt = Instant.now(clock);
        var models = discoveredModels(discovered, provider).stream()
                .map(model -> toContract(provider.id(), model, existing))
                .toList();
        return new AiModelDiscovery(
                channel.getId(),
                workspace,
                provider.id(),
                provider.descriptor().displayName(),
                channel.getProtocol(),
                provider.resolveBaseUrl(channel),
                provider.id(),
                discoveredAt,
                discoveredAt,
                models);
    }

    private static List<AiProviderModel> discoveredModels(
            List<AiProviderModel> discovered, AiProvider provider) {
        var merged = new LinkedHashMap<String, AiProviderModel>();
        for (var model : discovered) {
            if (provider.acceptsModel(model.modelId())) {
                merged.putIfAbsent(key(model.modelId()), model);
            }
        }
        return List.copyOf(merged.values());
    }

    private static String key(String modelId) {
        return modelId.toLowerCase(Locale.ROOT);
    }

    private static AiDiscoveredModel toContract(
            String providerId, AiProviderModel model, Map<String, AiModel> existing) {
        var protocol = defaultProtocol(model.modelType());
        var modelType = model.modelType();
        var capabilities = modelType.supported()
                ? List.of(modelType.capability())
                : List.<String>of();
        var modalities = new LinkedHashMap<String, Object>();
        if (!model.inputModalities().isEmpty()) {
            modalities.put("input", model.inputModalities());
        }
        if (!model.outputModalities().isEmpty()) {
            modalities.put("output", model.outputModalities());
        }
        var imported = existing.get(logicalName(providerId, model.modelId()));
        return new AiDiscoveredModel(
                model.modelId(),
                model.displayName(),
                null,
                modelType.value(),
                protocol,
                List.of(protocol),
                model.contextWindow(),
                model.maxOutputTokens(),
                capabilities,
                model.features(),
                Map.copyOf(modalities),
                Map.of(),
                catalog(providerId, model),
                model.source(),
                catalogStatus(model),
                imported == null ? null : imported.id());
    }

    private static String defaultProtocol(AiModelType modelType) {
        return switch (modelType) {
            case CHAT -> "chat-completions";
            case EMBEDDING -> "embeddings";
            case IMAGE -> "images";
            case RERANK -> "rerank";
            case VIDEO -> "videos";
            case AUDIO -> "audio";
            case THREE_D -> "3d";
            default -> "chat-completions";
        };
    }

    private static Map<String, Object> catalog(String providerId, AiProviderModel model) {
        var values = new LinkedHashMap<String, Object>();
        values.put("provider", providerId);
        values.put("model", model.modelId());
        values.put("source", model.source());
        values.putAll(model.catalog());
        return Map.copyOf(values);
    }

    private static String catalogStatus(AiProviderModel model) {
        return Boolean.TRUE.equals(model.catalog().get("retired")) ? "retired" : null;
    }

    public static String logicalName(String provider, String modelId) {
        return provider + "/" + modelId;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
