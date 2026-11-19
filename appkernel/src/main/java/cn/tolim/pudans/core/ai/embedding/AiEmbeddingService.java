package cn.tolim.pudans.core.ai.embedding;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiEmbeddingService {

    public static final String MODEL_MISSING_CODE = "ai-embedding-model-missing";

    public static final String PROVIDER_MISSING_CODE = "ai-embedding-provider-missing";

    public static final String ACCOUNT_MISSING_CODE = "ai-embedding-account-missing";

    public static final String PROTOCOL_UNSUPPORTED_CODE = "ai-embedding-protocol-unsupported";

    public static final String DIMENSION_MISMATCH_CODE = "ai-embedding-dimension-mismatch";

    private final AiSettingService settingService;

    private final ReactiveExtensionClient client;

    private final AiEmbeddingClient embeddingClient;

    public Mono<EmbeddingBatch> embed(List<String> inputs) {
        return embed(inputs, null);
    }

    public Mono<EmbeddingBatch> embed(List<String> inputs, String modelName) {
        var pending = inputs == null ? List.<String>of() : List.copyOf(inputs);
        return settingService.spec()
                .switchIfEmpty(Mono.error(modelMissing(null)))
                .flatMap(spec -> target(spec, modelName).flatMap(target -> embedBatches(target, pending)));
    }

    public Mono<float[]> embedOne(String input) {
        if (input == null) {
            return Mono.error(new IllegalArgumentException("嵌入输入不能为空。"));
        }
        return embed(List.of(input)).map(batch -> batch.vectors().get(0));
    }

    private Mono<EmbeddingTarget> target(AiSetting.Spec spec, String modelName) {
        var embedding = spec.getEmbedding() == null ? new AiSetting.Embedding() : spec.getEmbedding();
        var resolvedName = StringUtils.hasText(modelName)
                ? modelName
                : firstText(embedding.getKnowledgeBaseModel(), embedding.getMemoryModel(), embedding.getQueryModel());
        if (!StringUtils.hasText(resolvedName)) {
            return Mono.error(modelMissing(null));
        }
        return client.get(AiModel.class, resolvedName)
                .filter(AiEmbeddingService::isUsableModel)
                .switchIfEmpty(Mono.error(modelMissing(resolvedName)))
                .flatMap(model -> target(model, embedding));
    }

    private Mono<EmbeddingTarget> target(AiModel model, AiSetting.Embedding embedding) {
        var providerName = model.getSpec().getProvider();
        if (!StringUtils.hasText(providerName)) {
            return Mono.error(providerMissing(null));
        }
        return client.get(AiProvider.class, providerName)
                .filter(AiEmbeddingService::isEnabledProvider)
                .switchIfEmpty(Mono.error(providerMissing(providerName)))
                .flatMap(provider -> {
                    if (provider.getSpec().getProtocol() != AiProvider.Protocol.OPENAI_CHAT) {
                        return Mono.error(protocolUnsupported(provider.getSpec().getProtocol()));
                    }
                    return account(providerName)
                            .map(account -> new EmbeddingTarget(
                                    provider,
                                    account,
                                    model.getSpec().getModelCode(),
                                    expectedDimension(model, embedding),
                                    batchSize(embedding)));
                });
    }

    private Mono<AiProviderAccount> account(String providerName) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.provider", providerName))
                .build();
        return client.listAll(AiProviderAccount.class, options, defaultSort())
                .filter(account -> account.getSpec() != null && account.getSpec().isEnabled())
                .next()
                .switchIfEmpty(Mono.error(accountMissing(providerName)));
    }

    private Mono<EmbeddingBatch> embedBatches(EmbeddingTarget target, List<String> inputs) {
        if (inputs.isEmpty()) {
            return Mono.just(new EmbeddingBatch(target.modelCode(), target.dimension(), List.of()));
        }
        return Flux.fromIterable(partition(inputs, target.batchSize()))
                .concatMap(batch -> embeddingClient
                        .embed(target.provider(), target.account(), target.modelCode(), batch)
                        .map(vectors -> validate(batch.size(), vectors, target.dimension())))
                .collectList()
                .map(batches -> new EmbeddingBatch(target.modelCode(), target.dimension(), flatten(batches)));
    }

    private static List<float[]> validate(int expectedCount, List<float[]> vectors, int dimension) {
        if (vectors == null || vectors.size() != expectedCount) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    AiEmbeddingClient.UPSTREAM_ERROR_CODE,
                    "嵌入响应数量与请求不一致。");
        }
        for (var vector : vectors) {
            if (vector == null || vector.length != dimension) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        DIMENSION_MISMATCH_CODE,
                        "嵌入向量维度不一致：期望 " + dimension + "。");
            }
        }
        return vectors;
    }

    private static List<List<String>> partition(List<String> inputs, int batchSize) {
        var batches = new ArrayList<List<String>>();
        for (var start = 0; start < inputs.size(); start += batchSize) {
            batches.add(inputs.subList(start, Math.min(inputs.size(), start + batchSize)));
        }
        return batches;
    }

    private static List<float[]> flatten(List<List<float[]>> batches) {
        var vectors = new ArrayList<float[]>();
        batches.forEach(vectors::addAll);
        return vectors;
    }

    private static boolean isUsableModel(AiModel model) {
        return model.getSpec() != null
                && model.getSpec().isEnabled()
                && model.getSpec().getModelType() == AiModel.ModelType.EMBEDDING;
    }

    private static boolean isEnabledProvider(AiProvider provider) {
        return provider.getSpec() != null && provider.getSpec().isEnabled();
    }

    private static String firstText(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static int expectedDimension(AiModel model, AiSetting.Embedding embedding) {
        var modelDimension = model.getSpec().getEmbeddingDim();
        if (modelDimension != null && modelDimension > 0) {
            return modelDimension;
        }
        return embedding.getDimension() > 0 ? embedding.getDimension() : new AiSetting.Embedding().getDimension();
    }

    private static int batchSize(AiSetting.Embedding embedding) {
        return embedding.getBatchSize() > 0 ? embedding.getBatchSize() : new AiSetting.Embedding().getBatchSize();
    }

    private static ApiException modelMissing(String modelName) {
        var reason = modelName == null ? "未配置嵌入模型。" : "嵌入模型不可用：" + modelName + "。";
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MODEL_MISSING_CODE, reason);
    }

    private static ApiException providerMissing(String providerName) {
        var reason = providerName == null
                ? "嵌入模型未配置供应商。"
                : "嵌入供应商不可用：" + providerName + "。";
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PROVIDER_MISSING_CODE, reason);
    }

    private static ApiException accountMissing(String providerName) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ACCOUNT_MISSING_CODE,
                "嵌入供应商没有可用账号：" + providerName + "。");
    }

    private static ApiException protocolUnsupported(AiProvider.Protocol protocol) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, PROTOCOL_UNSUPPORTED_CODE, "暂不支持该嵌入协议：" + protocol + "。");
    }

    private record EmbeddingTarget(
            AiProvider provider, AiProviderAccount account, String modelCode, int dimension, int batchSize) {}
}
