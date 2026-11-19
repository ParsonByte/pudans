package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public final class AiProviderHttpSupport {

    private AiProviderHttpSupport() {}

    public static String endpoint(AiProvider provider, String path) {
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
        return base + (path.startsWith("/") ? path : "/" + path);
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

    public static AiChatIr withModelAndStream(AiChatIr request, AiModel model, boolean stream) {
        var source = Objects.requireNonNull(request, "聊天 IR 不能为空");
        var spec = model == null ? null : model.getSpec();
        var modelCode = source.requestParams().model();
        if (spec != null && StringUtils.hasText(spec.getModelCode())) {
            modelCode = spec.getModelCode();
        }
        return new AiChatIr(
                source.messages(),
                source.tools(),
                params(source.requestParams(), modelCode, source.requestParams().maxTokens()),
                stream);
    }

    public static AiChatIr withDefaultMaxTokens(AiChatIr request, Integer maxTokens) {
        var params = request.requestParams();
        if (params.maxTokens() != null || maxTokens == null) {
            return request;
        }
        return new AiChatIr(
                request.messages(),
                request.tools(),
                params(params, params.model(), maxTokens),
                request.stream());
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

    private static AiRequestParams params(AiRequestParams source, String model, Integer maxTokens) {
        return new AiRequestParams(
                model,
                source.temperature(),
                maxTokens,
                source.topP(),
                source.topK(),
                source.stop(),
                source.toolChoice(),
                source.extra());
    }
}
