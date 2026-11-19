package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.generation.AiTextGenerationService;
import cn.tolim.pudans.core.ai.generation.GenerationRequest;
import cn.tolim.pudans.core.ai.generation.GenerationResult;
import cn.tolim.pudans.core.ai.memory.DedupAction;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
final class MemoryAiSupport {

    private static final double TEMPERATURE = 0.1;

    private static final String CODE_INVALID = "ai-memory-generation-invalid";

    private final AiTextGenerationService generationService;

    private final AiSettingService settingService;

    private final ObjectMapper mapper;

    Mono<GenerationResult> generate(String systemPrompt, String userPrompt, int maxOutputTokens) {
        return routeGroup()
                .map(code -> new GenerationRequest(code, systemPrompt, userPrompt, maxOutputTokens, TEMPERATURE))
                .defaultIfEmpty(new GenerationRequest(
                        null, systemPrompt, userPrompt, maxOutputTokens, TEMPERATURE))
                .flatMap(generationService::generate);
    }

    String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw invalid("记忆生成请求序列化失败。", error);
        }
    }

    JsonNode parseObject(String content) {
        var json = jsonBody(content);
        try {
            var node = mapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw invalid("记忆生成结果必须是 JSON 对象。", null);
            }
            return node;
        } catch (JsonProcessingException error) {
            throw invalid("记忆生成结果不是合法的 JSON。", error);
        }
    }

    Map<String, Object> map(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    static String combine(String systemPrompt, String protocol) {
        return StringUtils.hasText(systemPrompt) ? systemPrompt.strip() + "\n\n" + protocol : protocol;
    }

    static String text(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        var text = value.textValue().strip();
        return text.isEmpty() ? null : text;
    }

    static int integer(JsonNode node, String field, int defaultValue) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.asInt(defaultValue);
        }
        if (!value.isTextual()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.textValue().strip());
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    static Integer optionalInteger(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        if (!value.isTextual()) {
            return null;
        }
        try {
            return Integer.parseInt(value.textValue().strip());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static double decimal(JsonNode node, String field, double defaultValue) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.asDouble(defaultValue);
        }
        if (!value.isTextual()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.textValue().strip());
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    static double confidence(JsonNode node, double defaultValue) {
        return Math.max(0, Math.min(1, decimal(node, "confidence", defaultValue)));
    }

    static List<Long> longArray(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        var result = new ArrayList<Long>(value.size());
        for (var item : value) {
            var parsed = longValue(item);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return List.copyOf(result);
    }

    static List<String> stringArray(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        var result = new ArrayList<String>(value.size());
        for (var item : value) {
            if (item != null && item.isTextual() && StringUtils.hasText(item.textValue())) {
                result.add(item.textValue().strip());
            }
        }
        return List.copyOf(result);
    }

    static MemoryType memoryType(JsonNode node, String field) {
        var value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return MemoryType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static DedupAction dedupAction(JsonNode node) {
        var value = text(node, "action");
        if (value == null) {
            return null;
        }
        try {
            return DedupAction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static ApiException invalid(String message, @Nullable Throwable cause) {
        return cause == null
                ? new ApiException(HttpStatus.BAD_GATEWAY, CODE_INVALID, message)
                : new ApiException(HttpStatus.BAD_GATEWAY, CODE_INVALID, message, cause);
    }

    private Mono<String> routeGroup() {
        return settingService.spec().flatMap(spec -> {
            var code = spec.getMemory() == null ? null : spec.getMemory().getExtractionRouteGroup();
            return StringUtils.hasText(code) ? Mono.just(code.strip()) : Mono.empty();
        });
    }

    private static String jsonBody(String content) {
        if (!StringUtils.hasText(content)) {
            throw invalid("记忆生成结果为空。", null);
        }
        var text = content.strip();
        if (text.startsWith("```")) {
            var firstLineEnd = text.indexOf('\n');
            text = firstLineEnd < 0 ? text.substring(3) : text.substring(firstLineEnd + 1);
            var closing = text.lastIndexOf("```");
            if (closing >= 0) {
                text = text.substring(0, closing);
            }
            text = text.strip();
        }
        var start = text.indexOf('{');
        var end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw invalid("记忆生成结果缺少 JSON 对象。", null);
        }
        return text.substring(start, end + 1);
    }

    private static Long longValue(@Nullable JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isIntegralNumber()) {
            return item.longValue();
        }
        if (!item.isTextual()) {
            return null;
        }
        try {
            return Long.parseLong(item.textValue().strip());
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
