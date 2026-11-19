package cn.tolim.pudans.core.ai.rerank;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AiRerankClient {

    static final String RERANK_PATH = "/v1/rerank";

    static final String UPSTREAM_ERROR_CODE = "ai-rerank-upstream-error";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final AiProviderAccountService accountService;

    public AiRerankClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.accountService = Objects.requireNonNull(accountService, "供应商账号服务不能为空");
    }

    public Mono<RerankResult> rerank(
            AiProvider provider, AiProviderAccount account, String modelCode, RerankRequest request) {
        return Mono.defer(() -> {
                    var body = requestBody(modelCode, request);
                    return accountService.apiKey(account)
                            .defaultIfEmpty("")
                            .flatMap(apiKey -> webClient.post()
                                    .uri(endpoint(provider))
                                    .headers(headers -> headers.addAll(buildHeaders(provider, apiKey)))
                                    .bodyValue(body)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("重排响应为空。", null))))
                            .map(this::parseResults);
                })
                .onErrorMap(error -> error instanceof ApiException ? error : upstreamError(error));
    }

    private String requestBody(String modelCode, RerankRequest request) {
        var root = mapper.createObjectNode();
        root.put("model", modelCode);
        root.put("query", request.query());
        var documents = root.putArray("documents");
        request.documents().forEach(documents::add);
        root.put("top_n", request.topK());
        return root.toString();
    }

    private RerankResult parseResults(String body) {
        var results = readObject(body).get("results");
        if (results == null || !results.isArray()) {
            throw upstreamError("重排响应缺少 results 数组。", null);
        }
        var scored = new ArrayList<RerankResult.ScoredIndex>();
        for (var position = 0; position < results.size(); position++) {
            var item = results.get(position);
            var index = item == null ? null : item.get("index");
            var relevance = item == null ? null : item.get("relevance_score");
            if (index == null || !index.isIntegralNumber() || !index.canConvertToInt()) {
                throw upstreamError("重排响应缺少有效索引。", null);
            }
            if (relevance == null || !relevance.isNumber() || !Double.isFinite(relevance.doubleValue())) {
                throw upstreamError("重排响应缺少有效分数。", null);
            }
            scored.add(new RerankResult.ScoredIndex(index.asInt(), relevance.doubleValue()));
        }
        return new RerankResult(List.copyOf(scored));
    }

    private JsonNode readObject(String body) {
        try {
            var node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw upstreamError("重排响应必须是 JSON 对象。", null);
            }
            return node;
        } catch (JsonProcessingException error) {
            throw upstreamError("无法解析重排响应。", error);
        }
    }

    private static HttpHeaders buildHeaders(AiProvider provider, String apiKey) {
        var headers = new HttpHeaders();
        var spec = provider == null ? null : provider.getSpec();
        if (spec != null && spec.getHeaders() != null) {
            spec.getHeaders().forEach((name, value) -> {
                if (StringUtils.hasText(name) && value != null) {
                    headers.set(name, value);
                }
            });
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private static String endpoint(AiProvider provider) {
        var spec = provider == null ? null : provider.getSpec();
        var baseUrl = spec == null ? null : spec.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("供应商未配置 Base URL");
        }
        var trimmed = baseUrl.strip();
        var scheme = URI.create(trimmed).getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("供应商 Base URL 只允许 http 或 https");
        }
        var base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return base + RERANK_PATH;
    }

    private static ApiException upstreamError(Throwable cause) {
        return upstreamError("重排服务调用失败。", cause);
    }

    private static ApiException upstreamError(String reason, Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, UPSTREAM_ERROR_CODE, reason, cause);
    }
}
