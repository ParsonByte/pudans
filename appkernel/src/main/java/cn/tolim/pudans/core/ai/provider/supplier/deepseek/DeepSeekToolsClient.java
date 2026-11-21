package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekToolsClient extends AbstractDeepSeekClient {

    static final String SEARCH_PATH = "/v1/tools/search";

    static final String FETCH_PATH = "/v1/tools/fetch";

    static final String OCR_PATH = "/v1/tools/ocr";

    static final String ASR_PATH = "/v1/tools/asr";

    static final String TTS_PATH = "/v1/tools/tts";

    static final String UPSTREAM_ERROR_CODE = "ai-provider-tool-upstream-error";

    private static final String DEFAULT_ERROR = "DeepSeek 工具服务调用失败。";

    private static final String FILE_FIELD = "file";

    public DeepSeekToolsClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        super(webClient, mapper, providerAccounts, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<SearchResult> search(AiProvider provider, AiProviderAccount account, SearchRequest request) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .post()
                                .uri(AiProviderHttpSupport.endpoint(provider, SEARCH_PATH))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .bodyValue(searchBody(request))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("搜索响应为空。", null))))
                        .map(this::parseSearch))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<FetchResult> fetch(AiProvider provider, AiProviderAccount account, String url) {
        return Mono.defer(() -> withApiKey(account, apiKey -> {
                            var body = mapper.createObjectNode().put("url", url);
                            return webClient()
                                    .post()
                                    .uri(AiProviderHttpSupport.endpoint(provider, FETCH_PATH))
                                    .headers(headers -> headers.addAll(
                                            headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                    .bodyValue(AiProviderJsonSupport.write(mapper, body, DeepSeekProtocol.NAME))
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("抓取响应为空。", null)));
                        })
                        .map(this::parseFetch))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<OcrResult> ocr(
            AiProvider provider,
            AiProviderAccount account,
            String filename,
            String contentType,
            byte[] content,
            String purpose) {
        return Mono.defer(() -> withApiKey(account, apiKey -> {
                            var body = new MultipartBodyBuilder();
                            body.part("purpose", purpose);
                            body.part(FILE_FIELD, new ByteArrayResource(content))
                                    .filename(filename(filename))
                                    .contentType(mediaType(contentType));
                            return webClient()
                                    .post()
                                    .uri(AiProviderHttpSupport.endpoint(provider, OCR_PATH))
                                    .headers(headers -> headers.addAll(
                                            headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                    .body(BodyInserters.fromMultipartData(body.build()))
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("OCR 响应为空。", null)));
                        })
                        .map(this::parseOcr))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<AsrResult> asr(
            AiProvider provider,
            AiProviderAccount account,
            String filename,
            String contentType,
            byte[] content) {
        return Mono.defer(() -> withApiKey(account, apiKey -> {
                            var body = new MultipartBodyBuilder();
                            body.part(FILE_FIELD, new ByteArrayResource(content))
                                    .filename(filename(filename))
                                    .contentType(mediaType(contentType));
                            return webClient()
                                    .post()
                                    .uri(AiProviderHttpSupport.endpoint(provider, ASR_PATH))
                                    .headers(headers -> headers.addAll(
                                            headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                    .body(BodyInserters.fromMultipartData(body.build()))
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("语音识别响应为空。", null)));
                        })
                        .map(this::parseAsr))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Flux<DataBuffer> tts(
            AiProvider provider,
            AiProviderAccount account,
            String text,
            String voiceId,
            String format,
            Boolean filterMarkdown) {
        return Flux.defer(() -> providerAccounts
                        .apiKey(account)
                        .defaultIfEmpty("")
                        .flatMapMany(apiKey -> {
                            var body = mapper.createObjectNode().put("text", text);
                            if (StringUtils.hasText(voiceId)) {
                                body.put("voice_id", voiceId);
                            }
                            if (StringUtils.hasText(format)) {
                                body.put("format", format);
                            }
                            if (filterMarkdown != null) {
                                body.put("filter_markdown", filterMarkdown);
                            }
                            return webClient()
                                    .post()
                                    .uri(AiProviderHttpSupport.endpoint(provider, TTS_PATH))
                                    .headers(headers -> headers.addAll(
                                            headers(provider, apiKey, MediaType.ALL)))
                                    .bodyValue(AiProviderJsonSupport.write(mapper, body, DeepSeekProtocol.NAME))
                                    .exchangeToFlux(response -> {
                                        if (response.statusCode().isError()) {
                                            return response.bodyToMono(String.class)
                                                    .defaultIfEmpty("")
                                                    .flatMapMany(error -> Flux.error(
                                                            upstreamError(response.statusCode().value(), error)));
                                        }
                                        return response.bodyToFlux(DataBuffer.class);
                                    });
                        }))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    private String searchBody(SearchRequest request) {
        var root = mapper.createObjectNode();
        root.put("query", request.query());
        if (request.count() != null) {
            root.put("count", request.count());
        }
        if (request.oriQuestion() != null) {
            root.put("ori_question", request.oriQuestion());
        }
        if (request.localization() != null) {
            root.put("localization", request.localization());
        }
        if (request.needContent() != null) {
            root.put("need_content", request.needContent());
        }
        return AiProviderJsonSupport.write(mapper, root, DeepSeekProtocol.NAME);
    }

    private SearchResult parseSearch(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var results = root.get("results");
        if (results == null || !results.isArray()) {
            throw new AiProtocolException("DeepSeek 搜索响应缺少 results 数组");
        }
        var items = new ArrayList<SearchItem>();
        for (var item : results) {
            items.add(new SearchItem(
                    doubleValue(item.get("rerank_score")),
                    AiProviderHttpSupport.text(item, "title"),
                    AiProviderHttpSupport.text(item, "url"),
                    AiProviderHttpSupport.text(item, "snippet"),
                    AiProviderHttpSupport.text(item, "summary"),
                    AiProviderHttpSupport.text(item, "content"),
                    AiProviderHttpSupport.text(item, "site_name"),
                    AiProviderHttpSupport.text(item, "site_icon"),
                    AiProviderHttpSupport.text(item, "date"),
                    AiProviderHttpSupport.integer(item, "authority")));
        }
        return new SearchResult(items);
    }

    private FetchResult parseFetch(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        return new FetchResult(
                AiProviderHttpSupport.text(root, "title"),
                AiProviderHttpSupport.text(root, "content"));
    }

    private OcrResult parseOcr(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var pages = root.get("pages");
        if (pages == null || !pages.isArray()) {
            throw new AiProtocolException("DeepSeek OCR 响应缺少 pages 数组");
        }
        var items = new ArrayList<OcrPage>();
        for (var page : pages) {
            items.add(new OcrPage(
                    AiProviderHttpSupport.integer(page, "index"),
                    AiProviderHttpSupport.text(page, "text"),
                    AiProviderHttpSupport.text(page, "finish_reason")));
        }
        var usageNode = root.get("usage");
        return new OcrResult(
                AiProviderHttpSupport.text(root, "id"),
                AiProviderHttpSupport.text(root, "object"),
                longValue(root.get("created")),
                AiProviderHttpSupport.text(root, "purpose"),
                AiProviderHttpSupport.integer(root, "page_count"),
                items,
                new OcrUsage(
                        AiProviderHttpSupport.integer(usageNode, "input_tokens"),
                        AiProviderHttpSupport.integer(usageNode, "output_tokens"),
                        AiProviderHttpSupport.integer(usageNode, "total_tokens")));
    }

    private AsrResult parseAsr(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        return new AsrResult(
                AiProviderHttpSupport.text(root, "request_id"),
                AiProviderHttpSupport.text(root, "text"),
                AiProviderHttpSupport.integer(root, "duration_ms"));
    }

    private static Double doubleValue(JsonNode value) {
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private static Long longValue(JsonNode value) {
        return value != null && value.isIntegralNumber() ? value.asLong() : null;
    }

    private static MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String filename(String filename) {
        return StringUtils.hasText(filename) ? filename.replaceAll("[\\r\\n]", "_") : FILE_FIELD;
    }

    public record SearchRequest(
            String query,
            Integer count,
            String oriQuestion,
            String localization,
            Boolean needContent) {}

    public record SearchResult(List<SearchItem> results) {}

    public record SearchItem(
            Double rerankScore,
            String title,
            String url,
            String snippet,
            String summary,
            String content,
            String siteName,
            String siteIcon,
            String date,
            Integer authority) {}

    public record FetchResult(String title, String content) {}

    public record OcrResult(
            String id,
            String object,
            Long created,
            String purpose,
            Integer pageCount,
            List<OcrPage> pages,
            OcrUsage usage) {}

    public record OcrPage(Integer index, String text, String finishReason) {}

    public record OcrUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {}

    public record AsrResult(String requestId, String text, Integer durationMs) {}
}
