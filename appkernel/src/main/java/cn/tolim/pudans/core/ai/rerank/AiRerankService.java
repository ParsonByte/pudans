package cn.tolim.pudans.core.ai.rerank;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jScoringModelFactory;
import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class AiRerankService {

    public static final String MODEL_MISSING_CODE = "ai-rerank-model-missing";

    public static final String PROVIDER_MISSING_CODE = "ai-rerank-provider-missing";

    public static final String ACCOUNT_MISSING_CODE = "ai-rerank-account-missing";

    public static final String PROTOCOL_UNSUPPORTED_CODE = "ai-rerank-protocol-unsupported";

    public static final String UPSTREAM_ERROR_CODE = "ai-rerank-upstream-error";

    private final ReactiveExtensionClient client;

    private final AiProviderAccountService accountService;

    private final LangChain4jScoringModelFactory scoringModelFactory;

    public Mono<RerankResult> rerank(String modelName, String query, List<String> documents, int topK) {
        var pending = documents == null ? List.<String>of() : List.copyOf(documents);
        if (!StringUtils.hasText(modelName)) {
            return Mono.error(modelMissing(null));
        }
        if (topK < 1) {
            return Mono.error(new IllegalArgumentException("重排数量必须大于 0。"));
        }
        return target(modelName)
                .flatMap(target -> rerank(
                        target.provider(), target.account(), target.model(), query, pending, topK))
                .onErrorMap(error -> error instanceof ApiException ? error : upstreamError(error));
    }

    public Mono<RerankResult> rerank(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            String query,
            List<String> documents,
            int topK) {
        if (topK < 1) {
            return Mono.error(new IllegalArgumentException("重排数量必须大于 0。"));
        }
        var protocol = provider == null || provider.getSpec() == null
                ? null
                : provider.getSpec().getProtocol();
        if (!scoringModelFactory.supports(protocol)) {
            return Mono.error(protocolUnsupported(protocol));
        }
        var pending = documents == null ? List.<String>of() : List.copyOf(documents);
        if (pending.isEmpty()) {
            return Mono.just(new RerankResult(List.of()));
        }
        var modelCode = model == null || model.getSpec() == null ? null : model.getSpec().getModelCode();
        if (!StringUtils.hasText(modelCode)) {
            return Mono.error(modelMissing(null));
        }
        return accountService
                .apiKey(account)
                .defaultIfEmpty("")
                .map(apiKey -> scoringModelFactory.create(provider, apiKey, modelCode))
                .flatMap(scoringModel -> Mono.fromCallable(() -> scoringModel.scoreAll(
                                pending.stream().map(TextSegment::from).toList(), query))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(response -> results(response.content(), pending.size()))
                        .map(result -> topResults(result, topK)))
                .onErrorMap(error -> error instanceof ApiException ? error : upstreamError(error));
    }

    private Mono<RerankTarget> target(String modelName) {
        return client.get(AiModel.class, modelName)
                .filter(AiRerankService::isRerankModel)
                .switchIfEmpty(Mono.error(modelMissing(modelName)))
                .flatMap(model -> {
                    var providerName = model.getSpec().getProvider();
                    if (!StringUtils.hasText(providerName)) {
                        return Mono.error(providerMissing(null));
                    }
                    return client.get(AiProvider.class, providerName)
                            .filter(AiRerankService::isEnabledProvider)
                            .switchIfEmpty(Mono.error(providerMissing(providerName)))
                            .flatMap(provider -> {
                                var protocol = provider.getSpec().getProtocol();
                                if (!scoringModelFactory.supports(protocol)) {
                                    return Mono.error(protocolUnsupported(protocol));
                                }
                                return accountService
                                        .firstEnabled(providerName)
                                        .switchIfEmpty(Mono.error(accountMissing(providerName)))
                                        .map(account -> new RerankTarget(provider, account, model));
                            });
                });
    }

    private static RerankResult results(List<Double> scores, int documentCount) {
        if (scores == null || scores.size() != documentCount) {
            throw upstreamError("重排分数数量与文档数量不一致。");
        }
        var scored = new ArrayList<RerankResult.ScoredIndex>(scores.size());
        for (var index = 0; index < scores.size(); index++) {
            var score = scores.get(index);
            if (score == null || !Double.isFinite(score)) {
                throw upstreamError("重排响应包含无效分数。");
            }
            scored.add(new RerankResult.ScoredIndex(index, score));
        }
        return new RerankResult(List.copyOf(scored));
    }

    private static RerankResult topResults(RerankResult result, int topK) {
        var ranked = result.results().stream()
                .sorted(Comparator.comparingDouble(RerankResult.ScoredIndex::score)
                        .reversed()
                        .thenComparingInt(RerankResult.ScoredIndex::index))
                .limit(topK)
                .toList();
        return new RerankResult(ranked);
    }

    private static boolean isRerankModel(AiModel model) {
        return model.getSpec() != null
                && model.getSpec().isEnabled()
                && model.getSpec().getModelType() == AiModel.ModelType.RERANK;
    }

    private static boolean isEnabledProvider(AiProvider provider) {
        return provider.getSpec() != null && provider.getSpec().isEnabled();
    }

    private static ApiException modelMissing(String modelName) {
        var reason = modelName == null ? "未配置重排模型。" : "重排模型不可用：" + modelName + "。";
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MODEL_MISSING_CODE, reason);
    }

    private static ApiException providerMissing(String providerName) {
        var reason = providerName == null
                ? "重排模型未配置供应商。"
                : "重排供应商不可用：" + providerName + "。";
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PROVIDER_MISSING_CODE, reason);
    }

    private static ApiException accountMissing(String providerName) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ACCOUNT_MISSING_CODE,
                "重排供应商没有可用账号：" + providerName + "。");
    }

    private static ApiException protocolUnsupported(AiProvider.Protocol protocol) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                PROTOCOL_UNSUPPORTED_CODE,
                "暂不支持该重排协议：" + protocol + "。");
    }

    private static ApiException upstreamError(String reason) {
        return new ApiException(HttpStatus.BAD_GATEWAY, UPSTREAM_ERROR_CODE, reason);
    }

    private static ApiException upstreamError(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                UPSTREAM_ERROR_CODE,
                "重排服务调用失败。",
                cause);
    }

    private record RerankTarget(AiProvider provider, AiProviderAccount account, AiModel model) {}
}