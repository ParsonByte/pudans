package cn.tolim.pudans.core.ai.runtime.sandbox;

import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

final class SandboxSupport {

    private static final Pattern SAFE_SHELL_ARGUMENT = Pattern.compile("[A-Za-z0-9_@%+=:,./-]+");

    private SandboxSupport() {}

    static String baseUrl(@Nullable String endpoint, String defaultUrl, String label) {
        var raw = StringUtils.hasText(endpoint) ? endpoint : defaultUrl;
        try {
            var validated = AiProviderHttpSupport.requireHttpUrl(raw, label);
            return validated.endsWith("/") ? validated.substring(0, validated.length() - 1) : validated;
        } catch (IllegalArgumentException error) {
            throw SandboxErrors.configInvalid(error.getMessage());
        }
    }

    static String requireText(@Nullable String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw SandboxErrors.configInvalid(label + " 不能为空。");
        }
        return value;
    }

    static String requireRegion(@Nullable String region, String provider) {
        if (!StringUtils.hasText(region)) {
            throw SandboxErrors.configInvalid(provider + " 提供方未配置 region。");
        }
        return region;
    }

    static JsonNode readJson(ObjectMapper mapper, String body, String provider, String action) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException error) {
            throw SandboxErrors.providerResponse(provider, action, error);
        }
    }

    static String writeJson(ObjectMapper mapper, Object value, String provider, String action) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw SandboxErrors.providerResponse(provider, action, error);
        }
    }

    static String text(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    static int integer(JsonNode node, String field, int defaultValue) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isIntegralNumber() ? value.asInt() : defaultValue;
    }

    static String shellCommand(String command, List<String> args) {
        if (args == null || args.isEmpty()) {
            return command;
        }
        var builder = new StringBuilder(command);
        for (var argument : args) {
            builder.append(' ').append(quote(argument));
        }
        return builder.toString();
    }

    static String quote(String value) {
        var argument = value == null ? "" : value;
        if (SAFE_SHELL_ARGUMENT.matcher(argument).matches()) {
            return argument;
        }
        return "'" + argument.replace("'", "'\"'\"'") + "'";
    }

    }