package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProtocol;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 供应商模型清单拉取与同步；接口和定时任务共用同一实现
@Service
@RequiredArgsConstructor
public class AiProviderModelSyncService {

    public static final String CODE_DISABLED = "ai-provider-disabled";

    public static final String CODE_NO_ACCOUNT = "ai-provider-account-missing";

    public static final String CODE_UNSUPPORTED = "ai-provider-models-unsupported";

    private final ReactiveExtensionClient client;

    private final AiProviderAccountService accountService;

    private final DeepSeekModelsClient deepSeekModelsClient;

    private final GenericModelsClient genericModelsClient;

    public Mono<DeepSeekModelsClient.ModelList> list(String providerName) {
        return enabledProvider(providerName)
                .flatMap(provider -> account(providerName).flatMap(account -> fetch(provider, account)));
    }

    public Mono<SyncResult> sync(String providerName) {
        return enabledProvider(providerName)
                .flatMap(provider -> account(providerName)
                        .flatMap(account -> fetch(provider, account))
                        .flatMap(remote -> synchronize(provider, remote)));
    }

    private Mono<AiProvider> enabledProvider(String providerName) {
        return client.get(AiProvider.class, providerName)
                .filter(provider -> provider.getSpec() != null && provider.getSpec().isEnabled())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_DISABLED, "供应商不存在或已禁用。")));
    }

    private Mono<AiProviderAccount> account(String providerName) {
        return accountService
                .firstEnabled(providerName)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_ACCOUNT, "供应商没有可用账号。")));
    }

    private Mono<DeepSeekModelsClient.ModelList> fetch(
            AiProvider provider, AiProviderAccount account) {
        var protocol = protocolOf(provider, account);
        if (DeepSeekProtocol.isDeepSeek(protocol)) {
            return deepSeekModelsClient.list(provider, account);
        }
        if (GenericModelsClient.supports(protocol)) {
            return genericModelsClient.list(provider, account);
        }
        return Mono.error(new ApiException(
                HttpStatus.BAD_REQUEST, CODE_UNSUPPORTED, "该供应商不支持获取模型列表。"));
    }

    private static AiProvider.Protocol protocolOf(AiProvider provider, AiProviderAccount account) {
        var override = account.getSpec() == null ? null : account.getSpec().getProtocolOverride();
        return override == null ? provider.getSpec().getProtocol() : override;
    }

    private Mono<SyncResult> synchronize(AiProvider provider, DeepSeekModelsClient.ModelList remote) {
        var providerName = provider.getMetadata().getName();
        var options = ListOptions.builder().andQuery(equal("spec.provider", providerName)).build();
        return client.listAll(AiModel.class, options, defaultSort()).collectList().flatMap(local -> {
            var localByCode = new HashMap<String, AiModel>();
            for (var model : local) {
                var code = model.getSpec() == null ? null : model.getSpec().getModelCode();
                if (StringUtils.hasText(code)) {
                    localByCode.putIfAbsent(code, model);
                }
            }
            var seen = new LinkedHashSet<String>();
            var added = new AtomicInteger();
            var updated = new AtomicInteger();
            var unchanged = new AtomicInteger();
            var synced = new ArrayList<SyncedModel>();
            return Flux.fromIterable(remote == null || remote.data() == null
                            ? List.<DeepSeekModelsClient.DeepSeekModel>of()
                            : remote.data())
                    .filter(item -> item != null && StringUtils.hasText(item.id()))
                    .filter(item -> seen.add(item.id()))
                    .concatMap(item -> {
                        var existing = localByCode.get(item.id());
                        if (existing == null) {
                            return client.create(newModel(providerName, item))
                                    .doOnNext(model -> {
                                        added.incrementAndGet();
                                        synced.add(describe(model));
                                    });
                        }
                        if (!applyRemote(existing, item)) {
                            unchanged.incrementAndGet();
                            synced.add(describe(existing));
                            return Mono.just(existing);
                        }
                        return client.update(existing).doOnNext(model -> {
                            updated.incrementAndGet();
                            synced.add(describe(model));
                        });
                    })
                    .then(Mono.fromSupplier(() -> {
                        var removed = localByCode.entrySet().stream()
                                .filter(entry -> !seen.contains(entry.getKey()))
                                .map(Map.Entry::getKey)
                                .sorted()
                                .toList();
                        return new SyncResult(
                                added.get(), updated.get(), unchanged.get(), removed, List.copyOf(synced));
                    }));
        });
    }

    private static AiModel newModel(String providerName, DeepSeekModelsClient.DeepSeekModel remote) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(modelName(providerName, remote.id()));
        model.setMetadata(metadata);
        var spec = new AiModel.Spec();
        spec.setProvider(providerName);
        spec.setModelCode(remote.id());
        spec.setDisplayName(StringUtils.hasText(remote.name()) ? remote.name() : remote.id());
        spec.setModelType(modelType(remote.id()));
        spec.setContextWindow(remote.contextWindow());
        spec.setMaxOutputTokens(remote.maxOutputTokens());
        var input = modalities(remote.inputModalities());
        if (!input.isEmpty()) {
            spec.setInputModalities(input);
        }
        var output = modalities(remote.outputModalities());
        if (!output.isEmpty()) {
            spec.setOutputModalities(output);
        }
        model.setSpec(spec);
        return model;
    }

    private static boolean applyRemote(AiModel model, DeepSeekModelsClient.DeepSeekModel remote) {
        if (model.getSpec() == null) {
            model.setSpec(new AiModel.Spec());
        }
        var spec = model.getSpec();
        var changed = false;
        if (StringUtils.hasText(remote.name()) && !Objects.equals(spec.getDisplayName(), remote.name())) {
            spec.setDisplayName(remote.name());
            changed = true;
        }
        if (remote.contextWindow() != null
                && !Objects.equals(spec.getContextWindow(), remote.contextWindow())) {
            spec.setContextWindow(remote.contextWindow());
            changed = true;
        }
        if (remote.maxOutputTokens() != null
                && !Objects.equals(spec.getMaxOutputTokens(), remote.maxOutputTokens())) {
            spec.setMaxOutputTokens(remote.maxOutputTokens());
            changed = true;
        }
        var input = modalities(remote.inputModalities());
        if (!input.isEmpty() && !input.equals(spec.getInputModalities())) {
            spec.setInputModalities(input);
            changed = true;
        }
        var output = modalities(remote.outputModalities());
        if (!output.isEmpty() && !output.equals(spec.getOutputModalities())) {
            spec.setOutputModalities(output);
            changed = true;
        }
        return changed;
    }

    private static List<AiModel.Modality> modalities(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<AiModel.Modality>();
        for (var value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                result.add(AiModel.Modality.valueOf(value.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // 远端不认识的模态直接跳过
            }
        }
        return List.copyOf(result);
    }

    private static AiModel.ModelType modelType(String modelCode) {
        var code = modelCode.toLowerCase(Locale.ROOT);
        if (code.contains("embed")) {
            return AiModel.ModelType.EMBEDDING;
        }
        if (code.contains("rerank")) {
            return AiModel.ModelType.RERANK;
        }
        return AiModel.ModelType.CHAT;
    }

    private static String modelName(String providerName, String modelCode) {
        var sanitized = modelCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]+", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (sanitized.isBlank()) {
            sanitized = "model";
        }
        var name = providerName + "-" + sanitized;
        return name.length() <= 63 ? name : name.substring(0, 63);
    }

    private static SyncedModel describe(AiModel model) {
        return new SyncedModel(
                model.getMetadata() == null ? null : model.getMetadata().getName(),
                model.getSpec() == null ? null : model.getSpec().getModelCode(),
                model.getSpec() == null ? null : model.getSpec().getDisplayName());
    }

    public record SyncResult(
            int added,
            int updated,
            int unchanged,
            List<String> removed,
            List<SyncedModel> models) {}

    public record SyncedModel(
            @Nullable String name, @Nullable String modelCode, @Nullable String displayName) {}
}