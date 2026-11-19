package cn.tolim.pudans.core.ai.embedding;

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
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AiEmbeddingClient {

    static final String EMBEDDINGS_PATH = "/v1/embeddings";

    static final String UPSTREAM_ERROR_CODE = "ai-embedding-upstream-error";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final AiProviderAccountService accountService;

    public AiEmbeddingClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.accountService = Objects.requireNonNull(accountService, "供应商账号服务不能为空");
    }

    public Mono<List<float[]>> embed(
            AiProvider provider, AiProviderAccount account, String modelCode, List<String> inputs) {
        return Mono.defer(() -> {
                    var body = requestBody(modelCode, inputs);
                    return accountService.apiKey(account)
                            .defaultIfEmpty("")
                            .flatMap(apiKey -> webClient.post()
                                    .uri(endpoint(provider))
                                    .headers(headers -> headers.addAll(buildHeaders(provider, apiKey)))
                                    .bodyValue(body)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("嵌入响应为空。", null))))
                            .map(responseBody -> parseVectors(responseBody, inputs.size()));
                })
                .onErrorMap(error -> error instanceof ApiException ? error : upstreamError(error));
    }

    private String requestBody(String modelCode, List<String> inputs) {
        var root = mapper.createObjectNode();
        root.put("model", modelCode);
        var input = root.putArray("input");
        inputs.forEach(input::add);
        return root.toString();
    }

    private List<float[]> parseVectors(String body, int expectedCount) {
        var data = readObject(body).get("data");
        if (data == null || !data.isArray()) {
            throw upstreamError("嵌入响应缺少 data 数组。", null);
        }
        var vectors = new TreeMap<Integer, float[]>();
        for (var position = 0; position < data.size(); position++) {
            var item = data.get(position);
            var embedding = item == null ? null : item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw upstreamError("嵌入响应缺少向量数据。", null);
            }
            vectors.put(index(item, position), vector(embedding));
        }
        if (vectors.size() != expectedCount) {
            throw upstreamError("嵌入响应数量与请求不一致。", null);
        }
        return new ArrayList<>(vectors.values());
    }

    private JsonNode readObject(String body) {
        try {
            var node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw upstreamError("嵌入响应必须是 JSON 对象。", null);
            }
            return node;
        } catch (JsonProcessingException error) {
            throw upstreamError("无法解析嵌入响应。", error);
        }
    }

    private static int index(JsonNode item, int position) {
        var index = item.get("index");
        return index != null && index.isIntegralNumber() ? index.asInt() : position;
    }

    private static float[] vector(JsonNode embedding) {
        var values = new float[embedding.size()];
        for (var position = 0; position < embedding.size(); position++) {
            var value = embedding.get(position);
            if (value == null || !value.isNumber()) {
                throw upstreamError("嵌入向量包含非数值元素。", null);
            }
            values[position] = value.floatValue();
        }
        return values;
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
        return base + EMBEDDINGS_PATH;
    }

    private static ApiException upstreamError(Throwable cause) {
        return upstreamError("嵌入服务调用失败。", cause);
    }

    private static ApiException upstreamError(String reason, Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, UPSTREAM_ERROR_CODE, reason, cause);
    }
}
