package cn.tolim.pudans.core.ai.rerank;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiRerankService {

    public static final String MODEL_MISSING_CODE = "ai-rerank-model-missing";

    public static final String PROVIDER_MISSING_CODE = "ai-rerank-provider-missing";

    public static final String ACCOUNT_MISSING_CODE = "ai-rerank-account-missing";

    public static final String PROTOCOL_UNSUPPORTED_CODE = "ai-rerank-protocol-unsupported";

    private final ReactiveExtensionClient client;

    private final AiRerankClient rerankClient;

    public Mono<RerankResult> rerank(String modelName, String query, List<String> documents, int topK) {
        var pending = documents == null ? List.<String>of() : List.copyOf(documents);
        if (!StringUtils.hasText(modelName)) {
            return Mono.error(modelMissing(null));
        }
        if (topK < 1) {
            return Mono.error(new IllegalArgumentException("重排数量必须大于 0。"));
        }
        return target(modelName)
                .flatMap(target -> pending.isEmpty()
                        ? Mono.just(new RerankResult(List.of()))
                        : rerankClient.rerank(
                                target.provider(),
                                target.account(),
                                target.modelCode(),
                                new RerankRequest(query, pending, topK)))
                .map(result -> validated(result, pending.size()))
                .map(result -> topResults(result, topK))
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
                                if (provider.getSpec().getProtocol() != AiProvider.Protocol.OPENAI_CHAT) {
                                    return Mono.error(protocolUnsupported(provider.getSpec().getProtocol()));
                                }
                                return account(providerName)
                                        .map(account -> new RerankTarget(
                                                provider, account, model.getSpec().getModelCode()));
                            });
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

    private static RerankResult validated(RerankResult result, int documentCount) {
        if (result == null || result.results() == null) {
            throw invalidIndex();
        }
        var indexes = new HashSet<Integer>();
        for (var scored : result.results()) {
            if (scored == null
                    || scored.index() < 0
                    || scored.index() >= documentCount
                    || !indexes.add(scored.index())) {
                throw invalidIndex();
            }
        }
        return result;
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

    private static ApiException invalidIndex() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                AiRerankClient.UPSTREAM_ERROR_CODE,
                "重排响应索引超出范围或重复。");
    }

    private static ApiException upstreamError(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                AiRerankClient.UPSTREAM_ERROR_CODE,
                "重排服务调用失败。",
                cause);
    }

    private record RerankTarget(AiProvider provider, AiProviderAccount account, String modelCode) {}
}
