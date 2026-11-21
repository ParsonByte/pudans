package cn.tolim.pudans.core.ai.embedding;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jEmbeddingModelFactory;
import cn.tolim.pudans.core.infra.exception.ApiException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class Embeddings {

    public static final String MODEL_MISSING_CODE = "ai-embedding-model-missing";

    public static final String PROVIDER_MISSING_CODE = "ai-embedding-provider-missing";

    public static final String ACCOUNT_MISSING_CODE = "ai-embedding-account-missing";

    public static final String PROTOCOL_UNSUPPORTED_CODE = "ai-embedding-protocol-unsupported";

    public static final String DIMENSION_MISMATCH_CODE = "ai-embedding-dimension-mismatch";

    public static final String INPUT_UNSUPPORTED_CODE = "ai-embedding-input-unsupported";

    private static final String UPSTREAM_ERROR_CODE = "ai-embedding-upstream-error";

    private final AiSettings aiSettings;

    private final ReactiveExtensionClient client;

    private final ProviderAccounts providerAccounts;

    private final LangChain4jEmbeddingModelFactory embeddingModelFactory;

    public Mono<EmbeddingBatch> embed(List<String> inputs) {
        return embed(inputs, null);
    }

    public Mono<EmbeddingBatch> embed(List<String> inputs, String modelName) {
        List<AiModelContent> contents = inputs == null
                ? new ArrayList<>()
                : inputs.stream().<AiModelContent>map(AiModelContent.Text::new).toList();
        return embedContents(contents, modelName);
    }

    public Mono<EmbeddingBatch> embedContents(List<AiModelContent> inputs, String modelName) {
        var pending = inputs == null ? List.<AiModelContent>of() : List.copyOf(inputs);
        return aiSettings.spec()
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
                .filter(Embeddings::isUsableModel)
                .switchIfEmpty(Mono.error(modelMissing(resolvedName)))
                .flatMap(model -> target(model, embedding));
    }

    private Mono<EmbeddingTarget> target(AiModel model, AiSetting.Embedding embedding) {
        var providerName = model.getSpec().getProvider();
        if (!StringUtils.hasText(providerName)) {
            return Mono.error(providerMissing(null));
        }
        return client.get(AiProvider.class, providerName)
                .filter(Embeddings::isEnabledProvider)
                .switchIfEmpty(Mono.error(providerMissing(providerName)))
                .flatMap(provider -> {
                    var protocol = provider.getSpec().getProtocol();
                    if (!LangChain4jEmbeddingModelFactory.supports(protocol)) {
                        return Mono.error(protocolUnsupported(protocol));
                    }
                    return account(providerName)
                            .map(account -> new EmbeddingTarget(
                                    provider,
                                    account,
                                    model,
                                    protocol,
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

    private Mono<EmbeddingBatch> embedBatches(EmbeddingTarget target, List<AiModelContent> inputs) {
        var modelCode = target.model().getSpec().getModelCode();
        if (inputs.isEmpty()) {
            return Mono.just(new EmbeddingBatch(modelCode, target.dimension(), List.of()));
        }
        var texts = texts(inputs);
        return providerAccounts
                .apiKey(target.account())
                .defaultIfEmpty("")
                .flatMapMany(apiKey -> {
                    var embeddingModel = embeddingModelFactory.embeddingModel(
                            target.provider(),
                            target.protocol(),
                            modelCode,
                            apiKey,
                            target.dimension());
                    return Flux.fromIterable(partition(texts, target.batchSize()))
                            .concatMap(batch -> Mono.fromCallable(() -> embeddingModel
                                            .embedAll(batch.stream()
                                                    .map(TextSegment::from)
                                                    .toList())
                                            .content())
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .switchIfEmpty(Mono.error(upstreamError("嵌入响应为空。")))
                                    .map(embeddings -> validate(
                                            batch.size(), vectors(embeddings), target.dimension())));
                })
                .collectList()
                .map(batches -> new EmbeddingBatch(modelCode, target.dimension(), flatten(batches)));
    }

    private static List<String> texts(List<AiModelContent> inputs) {
        var texts = new ArrayList<String>();
        for (var input : inputs) {
            if (input instanceof AiModelContent.Text text) {
                texts.add(text.text());
            } else {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, INPUT_UNSUPPORTED_CODE, "嵌入协议仅支持文本输入。");
            }
        }
        return texts;
    }

    private static List<float[]> vectors(List<Embedding> embeddings) {
        if (embeddings == null) {
            return List.of();
        }
        return embeddings.stream().map(Embedding::vector).toList();
    }

    private static List<float[]> validate(int expectedCount, List<float[]> vectors, int dimension) {
        if (vectors == null || vectors.size() != expectedCount) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    UPSTREAM_ERROR_CODE,
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

    private static <T> List<List<T>> partition(List<T> inputs, int batchSize) {
        var batches = new ArrayList<List<T>>();
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
                && model.getSpec().getModelType() == AiModel.ModelType.EMBEDDING
                && model.getSpec().effectiveCapabilities().contains(AiModel.Capability.EMBEDDING);
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

    private static ApiException upstreamError(String reason) {
        return new ApiException(HttpStatus.BAD_GATEWAY, UPSTREAM_ERROR_CODE, reason);
    }

    private record EmbeddingTarget(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiProvider.Protocol protocol,
            int dimension,
            int batchSize) {}
}
