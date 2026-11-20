package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public final class AiProviderHttpSupport {

    private AiProviderHttpSupport() {}

    public static String endpoint(AiProvider provider, String path) {
        var spec = provider == null ? null : provider.getSpec();
        var baseUrl = spec == null ? null : spec.getBaseUrl();
        var trimmed = requireHttpUrl(baseUrl, "供应商 Base URL");
        var base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    public static String requireHttpUrl(@Nullable String rawUrl, String label) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        var trimmed = rawUrl.strip();
        String scheme;
        try {
            scheme = URI.create(trimmed).getScheme();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(label + " 必须是合法的 URL", error);
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(label + " 只允许 http 或 https");
        }
        return trimmed;
    }

    public static void addProviderHeaders(HttpHeaders headers, AiProvider provider) {
        var spec = provider == null ? null : provider.getSpec();
        if (spec == null || spec.getHeaders() == null) {
            return;
        }
        spec.getHeaders().forEach((name, value) -> {
            if (StringUtils.hasText(name) && value != null) {
                headers.set(name, value);
            }
        });
    }

    public static ObjectNode readObject(ObjectMapper mapper, String body, String protocol) {
        try {
            var node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw new AiProtocolException(protocol + " 响应必须是 JSON 对象");
            }
            return (ObjectNode) node;
        } catch (JsonProcessingException e) {
            throw new AiProtocolException("无法解析 " + protocol + " 响应", e);
        }
    }

    public static String text(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    public static Integer integer(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isIntegralNumber() ? value.asInt() : null;
    }

    public static JsonNode value(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value;
    }

    }
