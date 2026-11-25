package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiChannelModelUpdateRequest;
import cn.tolim.pudans.contract.ai.dto.AiDiscoveredModel;
import cn.tolim.pudans.contract.ai.dto.AiModelCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelImportRequest;
import cn.tolim.pudans.contract.ai.dto.AiModelImportResult;
import cn.tolim.pudans.contract.ai.dto.AiModelUpdateRequest;
import cn.tolim.pudans.core.ai.AiChannelModels;
import cn.tolim.pudans.core.ai.AiModels;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiModelImports {

    private static final int LOGICAL_NAME_LIMIT = 128;

    private final AiModelDiscoveries discoveries;

    private final AiModels models;

    private final AiChannelModels channelModels;

    private final AiModelRepository modelRepository;

    private final AiChannelModelRepository channelModelRepository;

    private final TransactionalOperator transactions;

    private Clock clock = Clock.systemUTC();

    public Mono<AiModelImportResult> importModels(
            String workspaceId, UUID channelId, AiModelImportRequest request) {
        var requested = requestedKeys(request);
        return discoveries.discover(workspaceId, channelId).flatMap(discovery -> {
            var selected = discovery.models().stream()
                    .filter(model -> requested.isEmpty() || requested.contains(model.upstreamModelKey()))
                    .toList();
            var unknown = requested.isEmpty()
                    ? 0
                    : requested.stream()
                            .filter(key -> discovery.models().stream()
                                    .noneMatch(model -> model.upstreamModelKey().equals(key)))
                            .toList()
                            .size();
            var initialSkipped = discovery.models().size() - selected.size() + unknown;
            return Mono.zip(
                            modelRepository.findAll().collectMap(AiModel::logicalName),
                            channelModelRepository
                                    .findByChannelId(channelId)
                                    .collectMap(AiChannelModel::modelId))
                    .flatMap(existing -> Flux.fromIterable(selected)
                            .concatMap(model -> importOne(
                                    channelId,
                                    discovery.provider(),
                                    model,
                                    existing.getT1(),
                                    existing.getT2()))
                            .reduce(Counts.initial(initialSkipped), Counts::add)
                            .map(counts -> new AiModelImportResult(
                                    channelId,
                                    discovery.models().size(),
                                    counts.created(),
                                    counts.updated(),
                                    counts.skipped(),
                                    Instant.now(clock)))
                            .as(transactions::transactional));
        });
    }

    private Mono<Counts> importOne(
            UUID channelId,
            String providerId,
            AiDiscoveredModel discovered,
            Map<String, AiModel> modelsByLogicalName,
            Map<UUID, AiChannelModel> routesByModelId) {
        var type = AiModelType.find(discovered.modelType()).orElse(AiModelType.UNKNOWN);
        var logicalName = AiModelDiscoveries.logicalName(providerId, discovered.upstreamModelKey());
        if (!type.supported()
                || logicalName.length() > LOGICAL_NAME_LIMIT
                || discovered.contextWindow() != null && discovered.contextWindow() <= 0
                || discovered.maxOutputTokens() != null && discovered.maxOutputTokens() <= 0) {
            return Mono.just(Counts.initial(1));
        }
        var capabilities = capabilities(type, discovered);
        var existing = modelsByLogicalName.get(logicalName);
        var model = existing == null
                ? models.create(new AiModelCreateRequest(
                        logicalName,
                        discovered.displayName(),
                        discovered.description(),
                        capabilities,
                        discovered.contextWindow(),
                        discovered.maxOutputTokens(),
                        discovered.parameterSchema(),
                        AiSupport.ACTIVE))
                : models.update(
                        existing.id(),
                        new AiModelUpdateRequest(
                                existing.logicalName(),
                                discovered.displayName(),
                                discovered.description(),
                                capabilities,
                                discovered.contextWindow(),
                                discovered.maxOutputTokens(),
                                discovered.parameterSchema(),
                                existing.status()));
        return model.flatMap(saved -> upsertRoute(
                        channelId, discovered.upstreamModelKey(), saved, routesByModelId.get(saved.id()))
                .thenReturn(existing == null ? new Counts(1, 0, 0) : new Counts(0, 1, 0)));
    }

    private Mono<AiChannelModel> upsertRoute(
            UUID channelId,
            String upstreamModelKey,
            AiModel model,
            AiChannelModel existing) {
        var metadata = routeMetadata(upstreamModelKey, model);
        if (existing == null) {
            return channelModels.create(new AiChannelModelCreateRequest(
                    channelId,
                    model.id(),
                    upstreamModelKey,
                    100,
                    100,
                    true,
                    Map.of(),
                    metadata));
        }
        return channelModels.update(
                existing.id(),
                new AiChannelModelUpdateRequest(
                        channelId,
                        model.id(),
                        upstreamModelKey,
                        existing.priority(),
                        existing.weight(),
                        existing.enabled(),
                        existing.parameterOverrides(),
                        metadata));
    }

    private static Map<String, Object> capabilities(
            AiModelType type, AiDiscoveredModel discovered) {
        var details = new LinkedHashMap<String, Object>();
        details.put("type", type.value());
        details.put("protocol", discovered.protocol());
        details.put("protocols", discovered.protocols());
        details.put("source", discovered.source());
        if (discovered.catalog() != null && !discovered.catalog().isEmpty()) {
            details.put("catalog", discovered.catalog());
        }
        if (discovered.features() != null && !discovered.features().isEmpty()) {
            details.put("features", discovered.features());
        }
        var capabilities = new LinkedHashMap<String, Object>();
        capabilities.put(type.capability(), Map.copyOf(details));
        return Map.copyOf(capabilities);
    }

    private static Map<String, Object> routeMetadata(
            String upstreamModelKey, AiModel model) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("upstreamModelKey", upstreamModelKey);
        metadata.put("logicalName", model.logicalName());
        if (model.contextWindow() != null) {
            metadata.put("contextWindow", model.contextWindow());
        }
        if (model.maxOutputTokens() != null) {
            metadata.put("maxOutputTokens", model.maxOutputTokens());
        }
        metadata.put("capabilities", model.capabilities().keySet());
        return Map.copyOf(metadata);
    }

    private static LinkedHashSet<String> requestedKeys(AiModelImportRequest request) {
        var keys = new LinkedHashSet<String>();
        if (request == null || request.modelKeys() == null) {
            return keys;
        }
        for (var key : request.modelKeys()) {
            if (key != null && !key.isBlank()) {
                keys.add(key.trim());
            }
        }
        return keys;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record Counts(int created, int updated, int skipped) {

        static Counts initial(int skipped) {
            return new Counts(0, 0, skipped);
        }

        Counts add(Counts other) {
            return new Counts(
                    created + other.created,
                    updated + other.updated,
                    skipped + other.skipped);
        }
    }
}
