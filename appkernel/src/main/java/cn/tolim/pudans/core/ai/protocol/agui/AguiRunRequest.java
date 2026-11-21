package cn.tolim.pudans.core.ai.protocol.agui;

import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public record AguiRunRequest(
        String threadId, String runId, @Nullable String agentName, String input) {

    private static final String CODE_BODY_INVALID = "ai-agui-body-invalid";

    private static final String CODE_MESSAGES_INVALID = "ai-agui-messages-invalid";

    private static final String FORWARDED_AGENT = "agent";

    private static final String USER_ROLE = "user";

    public static AguiRunRequest parse(
            ObjectMapper mapper, @Nullable String body, @Nullable String headerAgent) {
        if (!StringUtils.hasText(body)) {
            throw error(CODE_BODY_INVALID, "请求体不能为空。");
        }
        RunAgentInput input;
        try {
            input = mapper.readValue(body, RunAgentInput.class);
        } catch (JsonProcessingException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_BODY_INVALID, "请求体不是有效的 JSON。", error);
        }
        var message = lastUserMessage(input.messages());
        var text = message == null ? "" : textOf(message.content());
        if (!StringUtils.hasText(text)) {
            throw error(CODE_MESSAGES_INVALID, "缺少本轮用户消息。");
        }
        var threadId = StringUtils.hasText(input.threadId())
                ? input.threadId()
                : "thread-" + UUID.randomUUID();
        var runId = StringUtils.hasText(input.runId())
                ? input.runId()
                : "run-" + UUID.randomUUID();
        return new AguiRunRequest(
                threadId, runId, agentName(input.forwardedProps(), headerAgent), text);
    }

    private static @Nullable Message lastUserMessage(@Nullable List<Message> messages) {
        if (messages == null) {
            return null;
        }
        Message last = null;
        for (var message : messages) {
            if (message != null && USER_ROLE.equalsIgnoreCase(message.role())) {
                last = message;
            }
        }
        return last;
    }

    private static String textOf(@Nullable JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isObject()) {
            return textPart(content);
        }
        if (!content.isArray()) {
            return "";
        }
        var text = new StringBuilder();
        for (var part : content) {
            if (part.isTextual()) {
                text.append(part.asText());
            } else if (part.isObject()) {
                text.append(textPart(part));
            }
        }
        return text.toString();
    }

    private static String textPart(JsonNode part) {
        return "text".equals(part.path("type").asText()) ? part.path("text").asText("") : "";
    }

    private static @Nullable String agentName(
            @Nullable Map<String, Object> forwardedProps, @Nullable String headerAgent) {
        var forwarded = forwardedProps == null ? null : forwardedProps.get(FORWARDED_AGENT);
        if (forwarded instanceof String name && StringUtils.hasText(name)) {
            return name;
        }
        return StringUtils.hasText(headerAgent) ? headerAgent : null;
    }

    private static ApiException error(String code, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, reason);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RunAgentInput(
            @Nullable String threadId,
            @Nullable String runId,
            @Nullable List<Message> messages,
            @Nullable Map<String, Object> forwardedProps) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(@Nullable String id, @Nullable String role, @Nullable JsonNode content) {}
}