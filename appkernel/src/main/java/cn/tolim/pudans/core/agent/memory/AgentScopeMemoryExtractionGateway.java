package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.core.agent.runtime.AgentChatModelFactory;
import cn.tolim.pudans.core.agent.runtime.AgentModelRouteResolver;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeMemoryExtractionGateway implements MemoryExtractionGateway {

    private static final int MAX_MEMORIES = 20;

    private static final int MAX_TRANSCRIPT_CHARS = 16_000;

    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.6");

    private static final String SYSTEM_PROMPT = """
            你是 Pudans 的记忆抽取器。只从用户和助手真实消息中抽取可复用的事实、偏好、
            约束、项目和场景。忽略工具输出中与用户长期记忆无关的内容。禁止编造消息中
            没有的信息。输出必须是 JSON 数组，不要输出 Markdown 或解释。每项字段为：
            layer（L1/L2/L3）、memoryType（intent/preference/fact/project/subject/
            conversation/creation）、canonicalKey、content、summary、confidence（0-1）、
            evidenceMessageIds（消息 UUID 数组）。没有可抽取内容时输出 []。
            """;

    private final AgentModelRouteResolver routes;

    private final AgentChatModelFactory models;

    @Override
    public Mono<List<MemoryExtractionResult>> extract(
            AgentSessionEntity session,
            UUID runId,
            List<AgentMessageEntity> messages) {
        var transcript = transcript(messages);
        if (transcript.isBlank()) {
            return Mono.just(List.of());
        }
        return routes
                .resolve(session.getWorkspaceId(), null)
                .map(models::create)
                .flatMap(model -> model
                        .stream(
                                List.of(systemMessage(), userMessage(transcript)),
                                List.of(),
                                GenerateOptions.builder()
                                        .temperature(0.1)
                                        .maxTokens(2000)
                                        .build())
                        .collectList()
                        .map(responses -> responses.stream()
                                .flatMap(response -> response.getContent().stream())
                                .filter(TextBlock.class::isInstance)
                                .map(TextBlock.class::cast)
                                .map(TextBlock::getText)
                                .reduce("", String::concat))
                        .map(this::parse)
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorMap(
                        error -> error instanceof ApiException api
                                && Set.of(
                                                "ai-model-default-missing",
                                                "ai-model-unavailable",
                                                "agent-model-channel-unavailable",
                                                "agent-channel-route-incomplete",
                                                "agent-channel-protocol-unsupported")
                                        .contains(api.getCode()),
                        error -> new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "memory-extraction-model-not-configured",
                                "当前工作区没有可用于记忆抽取的聊天模型。",
                                error));
    }

    private static Msg systemMessage() {
        return Msg.builder().role(MsgRole.SYSTEM).textContent(SYSTEM_PROMPT).build();
    }

    private static Msg userMessage(String transcript) {
        return Msg.builder()
                .role(MsgRole.USER)
                .textContent("真实消息如下：\n" + transcript)
                .build();
    }

    private static String transcript(List<AgentMessageEntity> messages) {
        var builder = new StringBuilder();
        for (var message : messages) {
            var text = message.toContract().content().get("text");
            if (!(text instanceof String value) || value.isBlank()) {
                continue;
            }
            var line = "[" + message.getId() + "] " + message.getRole() + ": " + value.strip();
            if (builder.length() + line.length() > MAX_TRANSCRIPT_CHARS) {
                break;
            }
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private List<MemoryExtractionResult> parse(String raw) {
        var json = jsonArray(raw);
        List<Map<String, Object>> values;
        try {
            values = JsonUtils.jsonToObject(
                    json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (RuntimeException error) {
            throw invalidOutput(error);
        }
        var results = new ArrayList<MemoryExtractionResult>();
        for (var value : values) {
            var result = result(value);
            if (result != null && results.size() < MAX_MEMORIES) {
                results.add(result);
            }
        }
        return List.copyOf(results);
    }

    private MemoryExtractionResult result(Map<String, Object> value) {
        var layer = text(value.get("layer")).toUpperCase();
        var memoryType = text(value.get("memoryType")).toLowerCase();
        var content = text(value.get("content"));
        if (!MemoryText.PERSONAL_LAYERS.contains(layer)
                || !MemoryText.MEMORY_TYPES.contains(memoryType)
                || content.isBlank()) {
            throw invalidOutput(null);
        }
        var confidence = number(value.get("confidence"));
        if (confidence.compareTo(MIN_CONFIDENCE) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            return null;
        }
        return new MemoryExtractionResult(
                layer,
                memoryType,
                MemoryText.canonicalKey(text(value.get("canonicalKey"))),
                content,
                optionalText(value.get("summary")),
                confidence,
                messageIds(value.get("evidenceMessageIds")));
    }

    private static List<UUID> messageIds(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        var ids = new LinkedHashSet<UUID>();
        for (var item : values) {
            try {
                ids.add(UUID.fromString(text(item)));
            } catch (IllegalArgumentException ignored) {
                // 忽略模型返回的非 UUID 证据引用。
            }
        }
        return List.copyOf(ids);
    }

    private static BigDecimal number(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(text(value));
        } catch (NumberFormatException error) {
            throw invalidOutput(error);
        }
    }

    private static String jsonArray(String raw) {
        var value = raw == null ? "" : raw.strip();
        if (value.startsWith("```")) {
            var firstLine = value.indexOf('\n');
            var lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                value = value.substring(firstLine + 1, lastFence).strip();
            }
        }
        var start = value.indexOf('[');
        var end = value.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw invalidOutput(null);
        }
        return value.substring(start, end + 1);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String optionalText(Object value) {
        return StringUtils.hasText(text(value)) ? text(value) : null;
    }

    private static ApiException invalidOutput(Throwable cause) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "memory-extraction-invalid-output",
                "记忆抽取模型返回了无法解析的结果。",
                cause);
    }
}
