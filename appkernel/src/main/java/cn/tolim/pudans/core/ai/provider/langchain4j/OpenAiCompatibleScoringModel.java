package cn.tolim.pudans.core.ai.provider.langchain4j;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class OpenAiCompatibleScoringModel implements ScoringModel {

    private static final String RERANK_PATH = "/v1/rerank";

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final AiProvider provider;

    private final String apiKey;

    private final String modelCode;

    public OpenAiCompatibleScoringModel(
            WebClient webClient,
            ObjectMapper mapper,
            AiProvider provider,
            String apiKey,
            String modelCode) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.provider = Objects.requireNonNull(provider, "供应商不能为空");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelCode = Objects.requireNonNull(modelCode, "模型编码不能为空");
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(List.of());
        }
        var body = body(segments, query);
        var response = webClient
                .post()
                .uri(AiProviderHttpSupport.endpoint(provider, RERANK_PATH))
                .headers(this::headers)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalStateException("重排响应为空。")))
                .block();
        return Response.from(scores(response, segments.size()));
    }

    private String body(List<TextSegment> segments, String query) {
        var root = mapper.createObjectNode();
        root.put("model", modelCode);
        root.put("query", query);
        var documents = root.putArray("documents");
        segments.forEach(segment -> documents.add(segment.text()));
        root.put("top_n", segments.size());
        return root.toString();
    }

    private void headers(HttpHeaders headers) {
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
    }

    private List<Double> scores(String body, int documentCount) {
        try {
            var root = mapper.readTree(body);
            var results = root == null ? null : root.get("results");
            if (results == null || !results.isArray()) {
                throw new IllegalStateException("重排响应缺少 results 数组。");
            }
            var scores = new ArrayList<Double>();
            for (var index = 0; index < documentCount; index++) {
                scores.add(null);
            }
            for (var item : results) {
                var index = item == null ? null : item.get("index");
                var score = item == null ? null : item.get("relevance_score");
                if (index == null || !index.isIntegralNumber() || !index.canConvertToInt()) {
                    throw new IllegalStateException("重排响应缺少有效索引。");
                }
                var position = index.asInt();
                if (position < 0 || position >= documentCount || scores.get(position) != null) {
                    throw new IllegalStateException("重排响应索引超出范围或重复。");
                }
                if (score == null || !score.isNumber() || !Double.isFinite(score.doubleValue())) {
                    throw new IllegalStateException("重排响应缺少有效分数。");
                }
                scores.set(position, score.doubleValue());
            }
            if (scores.stream().anyMatch(Objects::isNull)) {
                throw new IllegalStateException("重排响应缺少部分文档分数。");
            }
            return List.copyOf(scores);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法解析重排响应。", error);
        }
    }
}