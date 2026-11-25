package cn.tolim.pudans.core.ai.protocol.systemone;

import cn.tolim.pudans.core.ai.protocol.AiProtocolUrl;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SystemOneDecisionClient {

    private static final int MAX_CHOICES = 255;

    private static final int MAX_SCORE_LEVELS = 10;

    private final AiProviderHttp http;

    private final AiProviders providers;

    public Mono<DecisionResponse> decide(
            AiChannelEntity channel,
            Map<String, String> credentials,
            String model,
            Map<String, Object> state,
            Map<String, Question> questions) {
        var provider = providers.require(channel.getProvider());
        var endpoint = URI.create(AiProtocolUrl.join(
                provider.systemOneBaseUrl(channel.getBaseUrl()), provider.systemOnePath()));
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("state", state == null ? Map.of() : state);
        body.put("questions", encodeQuestions(questions));
        return http.postJson(
                        endpoint,
                        credentials,
                        provider.systemOneHeaders(),
                        Map.copyOf(body),
                        timeout(channel))
                .map(SystemOneDecisionClient::decode);
    }

    private static Map<String, Object> encodeQuestions(Map<String, Question> questions) {
        var encoded = new LinkedHashMap<String, Object>();
        if (questions == null) {
            return Map.of();
        }
        questions.forEach((id, question) -> encoded.put(id, encodeQuestion(question)));
        return Map.copyOf(encoded);
    }

    private static Map<String, Object> encodeQuestion(Question question) {
        var body = new LinkedHashMap<String, Object>();
        body.put("instructions", question.instructions());
        switch (question) {
            case YesNo yesNo -> {
                body.put("type", "noul");
                var criteria = new LinkedHashMap<String, String>();
                put(criteria, "true", yesNo.trueCriteria());
                put(criteria, "false", yesNo.falseCriteria());
                if (!criteria.isEmpty()) {
                    body.put("criteria", Map.copyOf(criteria));
                }
            }
            case Choice choice -> {
                if (choice.options().size() > MAX_CHOICES) {
                    throw new IllegalArgumentException("System One 最多支持 255 个选项。");
                }
                body.put("type", "choice");
                body.put("criteria", choice.options());
            }
            case Score score -> {
                if (score.levels().size() > MAX_SCORE_LEVELS) {
                    throw new IllegalArgumentException("System One 最多支持 10 个评分级别。");
                }
                body.put("type", "score");
                body.put("criteria", score.levels());
            }
        }
        return Map.copyOf(body);
    }

    private static DecisionResponse decode(String body) {
        try {
            var root = JsonUtils.mapper().readTree(body);
            var answers = new LinkedHashMap<String, Answer>();
            var nodes = root.path("answers");
            if (nodes.isObject()) {
                nodes.fields().forEachRemaining(
                        entry -> answers.put(entry.getKey(), decodeAnswer(entry.getValue())));
            }
            return new DecisionResponse(
                    text(root.path("model")),
                    Map.copyOf(answers),
                    decodeUsage(root.path("usage")));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("无法解析 System One 决策响应。", error);
        }
    }

    private static Answer decodeAnswer(JsonNode node) {
        var type = text(node.path("type"));
        return switch (type) {
            case "noul" -> new Answer(
                    type, number(node.path("noul")), null, null, Map.of(), null, Map.of());
            case "choice" -> new Answer(
                    type,
                    null,
                    text(node.path("choice")),
                    null,
                    probabilities(node.path("probabilities")),
                    number(node.path("confidence")),
                    Map.of());
            case "score" -> new Answer(
                    type,
                    null,
                    null,
                    number(node.path("score")),
                    probabilities(node.path("probabilities")),
                    number(node.path("confidence")),
                    legend(node.path("legend")));
            default -> throw new IllegalArgumentException("不支持的 System One 答案类型：" + type);
        };
    }

    private static Usage decodeUsage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new Usage(
                tokens(node.get("input_tokens")),
                tokens(node.get("output_tokens")));
    }

    private static Map<String, Double> probabilities(JsonNode node) {
        var values = new LinkedHashMap<String, Double>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                var value = number(entry.getValue());
                if (value != null) {
                    values.put(entry.getKey(), value);
                }
            });
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> legend(JsonNode node) {
        var values = new LinkedHashMap<String, String>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                var value = text(entry.getValue());
                if (value != null) {
                    values.put(entry.getKey(), value);
                }
            });
        }
        return Map.copyOf(values);
    }

    private static Integer tokens(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        var value = number(node);
        if (value == null || value < 0 || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            throw new IllegalArgumentException("System One token 计数无效。");
        }
        return value.intValue();
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.textValue().isBlank() ? node.textValue() : null;
    }

    private static Double number(JsonNode node) {
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) {
            return null;
        }
        return node.doubleValue();
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static Duration timeout(AiChannelEntity channel) {
        return Duration.ofMillis(
                channel.getTimeoutMs() == null || channel.getTimeoutMs() <= 0
                        ? 60_000
                        : channel.getTimeoutMs());
    }

    public sealed interface Question permits YesNo, Choice, Score {

        String instructions();
    }

    public record YesNo(
            String instructions, String trueCriteria, String falseCriteria) implements Question {}

    public record Choice(String instructions, List<String> options) implements Question {

        public Choice {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Score(String instructions, List<String> levels) implements Question {

        public Score {
            levels = levels == null ? List.of() : List.copyOf(levels);
        }
    }

    public record Answer(
            String type,
            Double probability,
            String choice,
            Double score,
            Map<String, Double> probabilities,
            Double confidence,
            Map<String, String> legend) {}

    public record Usage(Integer inputTokens, Integer outputTokens) {}

    public record DecisionResponse(
            String model, Map<String, Answer> answers, Usage usage) {}
}
