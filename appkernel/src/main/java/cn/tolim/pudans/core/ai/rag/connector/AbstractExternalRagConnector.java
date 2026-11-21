package cn.tolim.pudans.core.ai.rag.connector;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

abstract class AbstractExternalRagConnector implements ExternalRagConnector {

    private static final int ERROR_BODY_LIMIT = 200;

    private final WebClient webClient;

    private final ObjectMapper mapper = JsonUtils.mapper();

    protected AbstractExternalRagConnector(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
    }

    protected ObjectMapper mapper() {
        return mapper;
    }

    protected Mono<JsonNode> postJson(ExternalRagTarget target, String uri, ObjectNode body) {
        return webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(target.apiKey())) {
                        headers.setBearerAuth(target.apiKey());
                    }
                })
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .defaultIfEmpty("{}")
                .map(this::readTree)
                .onErrorMap(WebClientResponseException.class, this::upstreamFailure)
                .onErrorMap(WebClientRequestException.class, this::connectionFailure);
    }

    protected String baseUrl(String endpoint) {
        try {
            var validated = AiProviderHttpSupport.requireHttpUrl(endpoint, "外部 RAG endpoint");
            return validated.endsWith("/") ? validated.substring(0, validated.length() - 1) : validated;
        } catch (IllegalArgumentException error) {
            throw configError(error.getMessage());
        }
    }

    protected ExternalRagException configError(String message) {
        return new ExternalRagException("外部 RAG 连接器 " + provider().name() + " 配置错误：" + message);
    }

    protected ExternalRagException responseError(String message) {
        return new ExternalRagException("外部 RAG 连接器 " + provider().name() + " 返回错误：" + message);
    }

    protected static String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.strip();
            }
        }
        return null;
    }

    protected static String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            var text = value.asText();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    protected static Double firstDouble(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            var parsed = value.asDouble(Double.NaN);
            if (Double.isFinite(parsed)) {
                return parsed;
            }
        }
        return null;
    }

    protected static Integer firstInt(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            var parsed = value.asInt(Integer.MIN_VALUE);
            if (parsed != Integer.MIN_VALUE) {
                return parsed;
            }
        }
        return null;
    }

    protected static ArrayNode firstArray(JsonNode root, String... paths) {
        for (var path : paths) {
            var value = at(root, path);
            if (value != null && value.isArray()) {
                return (ArrayNode) value;
            }
        }
        return null;
    }

    protected static JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (var segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private JsonNode readTree(String body) {
        try {
            var node = mapper.readTree(body);
            return node == null ? mapper.createObjectNode() : node;
        } catch (JsonProcessingException error) {
            throw new ExternalRagException(
                    "外部 RAG 连接器 " + provider().name() + " 响应不是有效 JSON。", error);
        }
    }

    private ExternalRagException upstreamFailure(WebClientResponseException error) {
        return new ExternalRagException(
                "外部 RAG 连接器 " + provider().name() + " 返回 HTTP " + error.getStatusCode().value()
                        + "：" + errorBody(error.getResponseBodyAsString()),
                error);
    }

    private ExternalRagException connectionFailure(WebClientRequestException error) {
        return new ExternalRagException("外部 RAG 连接器 " + provider().name() + " 请求失败。", error);
    }

    private static String errorBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "无响应内容";
        }
        var normalized = body.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= ERROR_BODY_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}