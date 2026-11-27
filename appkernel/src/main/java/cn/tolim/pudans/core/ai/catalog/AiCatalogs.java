package cn.tolim.pudans.core.ai.catalog;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.CatalogEntry;
import cn.tolim.pudans.contract.ai.CatalogKind;
import cn.tolim.pudans.contract.ai.dto.AiAssetCatalog;
import cn.tolim.pudans.contract.ai.dto.AiCatalogItem;
import cn.tolim.pudans.contract.ai.dto.AiChatCatalog;
import cn.tolim.pudans.contract.ai.dto.AiCreationCatalog;
import cn.tolim.pudans.contract.ai.dto.AiExploreCatalog;
import cn.tolim.pudans.contract.ai.dto.AiMemoryCatalog;
import cn.tolim.pudans.contract.ai.dto.AiModelCatalogItem;
import cn.tolim.pudans.contract.ai.dto.AiProviderCapabilityCode;
import cn.tolim.pudans.contract.ai.dto.AiSandboxCatalog;
import cn.tolim.pudans.contract.ai.dto.AiSkillDescriptor;
import cn.tolim.pudans.contract.ai.dto.AiToolDescriptor;
import cn.tolim.pudans.contract.ai.dto.AiUnderstandingCatalog;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.catalog.CatalogEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiCatalogs {

    private static final List<AiProviderCapabilityCode> CREATION_MEDIA_CAPABILITIES = List.of(
            AiProviderCapabilityCode.IMAGE,
            AiProviderCapabilityCode.VIDEO,
            AiProviderCapabilityCode.AUDIO);

    private final CatalogEntryRepository entries;

    private final AiModelRepository models;

    private final ObjectProvider<AiCapabilityRegistry> capabilityRegistry;

    private Clock clock = Clock.systemUTC();

    public Flux<AiModelCatalogItem> models() {
        return models.findAvailable().map(AiCatalogs::modelItem);
    }

    public Mono<AiCreationCatalog> creation() {
        var now = Instant.now(clock);
        return Mono.zip(
                        items(CatalogKind.CREATION_MEDIA_MODE, now).collectList(),
                        items(CatalogKind.CREATION_OPERATION, now).collectList(),
                        items(CatalogKind.CREATION_PARAMETER_OPTION, now).collectList(),
                        models.findAvailable().collectList())
                .map(tuple -> {
                    var mediaModes = tuple.getT1();
                    var operations = tuple.getT2();
                    var parameterOptions = tuple.getT3();
                    var available = tuple.getT4();
                    var result = new ArrayList<AiCreationCatalog.AiCreationMediaMode>();
                    var configuredKeys = new HashSet<String>();
                    for (var mode : mediaModes) {
                        configuredKeys.add(mode.key());
                        result.add(creationMode(
                                mode.key(), mode.label(), operations, parameterOptions, available));
                    }
                    for (var capability : CREATION_MEDIA_CAPABILITIES) {
                        if (configuredKeys.contains(capability.code())
                                || available.stream().noneMatch(model -> supports(model, capability.code()))) {
                            continue;
                        }
                        result.add(creationMode(
                                capability.code(),
                                capability.label(),
                                operations,
                                parameterOptions,
                                available));
                    }
                    return new AiCreationCatalog(List.copyOf(result));
                });
    }

    public Mono<AiChatCatalog> chat() {
        var now = Instant.now(clock);
        return Mono.zip(
                        items(CatalogKind.CHAT_ANSWER_MODE, now).collectList(),
                        items(CatalogKind.CHAT_REASONING_LEVEL, now).collectList(),
                        models.findAvailable().collectList(),
                        tools().collectList())
                .map(tuple -> new AiChatCatalog(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3().stream()
                                .filter(model -> supports(model, "chat"))
                                .map(AiCatalogs::modelItem)
                                .toList(),
                        tuple.getT4()));
    }

    public Flux<AiToolDescriptor> tools() {
        var registry = capabilityRegistry.getIfAvailable();
        if (registry == null) {
            return Flux.empty();
        }
        return registry.tools().filter(AiToolDescriptor::enabled);
    }

    public Flux<AiSkillDescriptor> skills() {
        var registry = capabilityRegistry.getIfAvailable();
        if (registry == null) {
            return Flux.empty();
        }
        return registry.skills().filter(AiSkillDescriptor::enabled);
    }

    public Mono<AiAssetCatalog> assets() {
        var now = Instant.now(clock);
        return Mono.zip(
                        items(CatalogKind.ASSET_KIND, now).collectList(),
                        items(CatalogKind.ASSET_SOURCE, now).collectList())
                .map(tuple -> new AiAssetCatalog(tuple.getT1(), tuple.getT2()));
    }

    public Mono<AiUnderstandingCatalog> understanding() {
        return items(CatalogKind.UNDERSTANDING_DIMENSION, Instant.now(clock))
                .collectList()
                .map(AiUnderstandingCatalog::new);
    }

    public Mono<AiMemoryCatalog> memory() {
        var now = Instant.now(clock);
        return Mono.zip(
                        items(CatalogKind.MEMORY_TYPE, now).collectList(),
                        items(CatalogKind.MEMORY_LAYER, now).collectList())
                .map(tuple -> new AiMemoryCatalog(tuple.getT1(), tuple.getT2()));
    }

    public Mono<AiSandboxCatalog> sandbox() {
        var now = Instant.now(clock);
        return Mono.zip(
                        items(CatalogKind.SANDBOX_PROVIDER, now).collectList(),
                        items(CatalogKind.SANDBOX_TEMPLATE, now).collectList(),
                        items(CatalogKind.SANDBOX_RESOURCE_PROFILE, now).collectList())
                .map(tuple -> new AiSandboxCatalog(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    public Mono<AiExploreCatalog> explore() {
        var now = Instant.now(clock);
        return Mono.zip(
                        items(CatalogKind.EXPLORE_CATEGORY, now).collectList(),
                        items(CatalogKind.EXPLORE_FILTER, now).collectList())
                .map(tuple -> new AiExploreCatalog(tuple.getT1(), tuple.getT2()));
    }

    private Flux<AiCatalogItem> items(CatalogKind kind, Instant at) {
        return entries.findActiveByKind(kind.code(), at).map(AiCatalogs::item);
    }

    private static AiCatalogItem item(CatalogEntry entry) {
        return new AiCatalogItem(
                entry.entryKey(),
                entry.label(),
                entry.description(),
                entry.parentKey(),
                entry.sortOrder(),
                entry.metadata());
    }

    private static AiModelCatalogItem modelItem(AiModel model) {
        var capabilities = new ArrayList<>(model.capabilities().keySet());
        capabilities.sort(String::compareTo);
        return new AiModelCatalogItem(
                model.id(),
                model.logicalName(),
                model.displayName(),
                model.description(),
                List.copyOf(capabilities),
                model.contextWindow(),
                model.maxOutputTokens(),
                model.parameterSchema());
    }

    private static AiCreationCatalog.AiCreationModel creationModel(AiModel model) {
        return new AiCreationCatalog.AiCreationModel(
                model.id(),
                model.logicalName(),
                model.displayName(),
                parameterDefinitions(model.parameterSchema()),
                mapValue(model.parameterSchema(), "limits"),
                model.parameterSchema());
    }

    private static AiCreationCatalog.AiCreationMediaMode creationMode(
            String key,
            String label,
            List<AiCatalogItem> operations,
            List<AiCatalogItem> parameterOptions,
            List<AiModel> available) {
        var modeOperations = operations.stream()
                .filter(item -> key.equals(item.parentKey()))
                .toList();
        var modeOptions = parameterOptions.stream()
                .filter(item -> key.equals(item.parentKey()))
                .toList();
        var modeModels = available.stream()
                .filter(model -> supports(model, key))
                .map(AiCatalogs::creationModel)
                .toList();
        return new AiCreationCatalog.AiCreationMediaMode(
                key, label, modeOperations, modeOptions, modeModels);
    }

    private static boolean supports(AiModel model, String capability) {
        return model.capabilities() != null && model.capabilities().containsKey(capability);
    }

    private static List<Map<String, Object>> parameterDefinitions(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }
        var direct = listValue(schema, "params");
        if (!direct.isEmpty()) {
            return direct;
        }
        var parameters = listValue(schema, "parameters");
        if (!parameters.isEmpty()) {
            return parameters;
        }
        var properties = mapValue(schema, "properties");
        if (properties.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : properties.entrySet()) {
            var definition = new LinkedHashMap<String, Object>();
            definition.put("key", entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> values) {
                values.forEach((key, value) -> definition.put(String.valueOf(key), value));
            }
            result.add(Map.copyOf(definition));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> listValue(Map<String, Object> schema, String key) {
        if (!(schema.get(key) instanceof List<?> values)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var value : values) {
            if (value instanceof Map<?, ?> map) {
                var item = new LinkedHashMap<String, Object>();
                map.forEach((entryKey, entryValue) ->
                        item.put(String.valueOf(entryKey), entryValue));
                result.add(Map.copyOf(item));
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> mapValue(Map<String, Object> schema, String key) {
        if (schema == null || !(schema.get(key) instanceof Map<?, ?> values)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        values.forEach((entryKey, entryValue) ->
                result.put(String.valueOf(entryKey), entryValue));
        return Map.copyOf(result);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
