package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderFile;
import cn.tolim.pudans.core.ai.provider.AiProviderFilePage;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekFilesClient extends AbstractDeepSeekClient {

    static final String UPSTREAM_ERROR_CODE = "ai-provider-file-upstream-error";

    private static final String DEFAULT_ERROR = "文件服务调用失败。";

    private static final String PURPOSE_FIELD = "purpose";

    private static final String FILE_FIELD = "file";

    private static final String EXPIRES_ANCHOR_FIELD = "expires_after[anchor]";

    private static final String EXPIRES_SECONDS_FIELD = "expires_after[seconds]";

    private static final String CREATED_AT_ANCHOR = "created_at";

    public DeepSeekFilesClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<AiProviderFile> upload(
            AiProvider provider,
            AiProviderAccount account,
            String filename,
            String contentType,
            byte[] content,
            String purpose,
            Long expiresAfterSeconds) {
        return Mono.defer(() -> withApiKey(account, apiKey -> {
                            var body = new MultipartBodyBuilder();
                            body.part(PURPOSE_FIELD, purpose);
                            if (expiresAfterSeconds != null) {
                                body.part(EXPIRES_ANCHOR_FIELD, CREATED_AT_ANCHOR);
                                body.part(EXPIRES_SECONDS_FIELD, expiresAfterSeconds.toString());
                            }
                            body.part(FILE_FIELD, new ByteArrayResource(content))
                                    .filename(filename(filename))
                                    .contentType(mediaType(contentType));
                            return webClient()
                                    .post()
                                    .uri(AiProviderHttpSupport.endpoint(provider, DeepSeekProtocol.FILES_PATH))
                                    .headers(headers -> headers.addAll(
                                            headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                    .body(BodyInserters.fromMultipartData(body.build()))
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .switchIfEmpty(Mono.error(upstreamError("文件上传响应为空。", null)));
                        })
                        .map(this::parseFile))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<AiProviderFilePage> list(
            AiProvider provider,
            AiProviderAccount account,
            Integer limit,
            String order,
            String after,
            String purpose) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .get()
                                .uri(listUri(provider, limit, order, after, purpose))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("文件列表响应为空。", null))))
                        .map(this::parsePage))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<AiProviderFile> get(AiProvider provider, AiProviderAccount account, String fileId) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .get()
                                .uri(fileUri(provider, fileId))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("文件响应为空。", null))))
                        .map(this::parseFile))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    public Mono<Void> delete(AiProvider provider, AiProviderAccount account, String fileId) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .delete()
                                .uri(fileUri(provider, fileId))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class))
                        .then())
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    private static URI listUri(
            AiProvider provider, Integer limit, String order, String after, String purpose) {
        var builder = UriComponentsBuilder.fromUriString(
                AiProviderHttpSupport.endpoint(provider, DeepSeekProtocol.FILES_PATH));
        if (StringUtils.hasText(after)) {
            builder.queryParam("after", after);
        }
        if (limit != null) {
            builder.queryParam("limit", limit);
        }
        if (StringUtils.hasText(order)) {
            builder.queryParam("order", order);
        }
        if (StringUtils.hasText(purpose)) {
            builder.queryParam("purpose", purpose);
        }
        return builder.build().encode().toUri();
    }

    private static URI fileUri(AiProvider provider, String fileId) {
        return UriComponentsBuilder.fromUriString(
                        AiProviderHttpSupport.endpoint(provider, DeepSeekProtocol.FILES_PATH))
                .pathSegment(fileId)
                .build()
                .encode()
                .toUri();
    }

    private AiProviderFile parseFile(String body) {
        return file(AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME));
    }

    private AiProviderFilePage parsePage(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var data = root.get("data");
        if (data == null || !data.isArray()) {
            throw new AiProtocolException("DeepSeek 文件列表响应缺少 data 数组");
        }
        var files = new ArrayList<AiProviderFile>();
        for (var item : data) {
            files.add(file(item));
        }
        return new AiProviderFilePage(
                AiProviderHttpSupport.text(root, "object"),
                files,
                AiProviderHttpSupport.text(root, "first_id"),
                AiProviderHttpSupport.text(root, "last_id"),
                booleanValue(root.get("has_more")));
    }

    private static AiProviderFile file(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new AiProtocolException("DeepSeek 文件响应必须是 JSON 对象");
        }
        var id = AiProviderHttpSupport.text(node, "id");
        if (!StringUtils.hasText(id)) {
            throw new AiProtocolException("DeepSeek 文件响应缺少 id");
        }
        return new AiProviderFile(
                id,
                AiProviderHttpSupport.text(node, "object"),
                longValue(node.get("bytes")),
                longValue(node.get("created_at")),
                AiProviderHttpSupport.text(node, "filename"),
                AiProviderHttpSupport.text(node, "purpose"),
                longValue(node.get("expires_at")));
    }

    private static Long longValue(JsonNode value) {
        return value != null && value.isIntegralNumber() ? value.asLong() : null;
    }

    private static boolean booleanValue(JsonNode value) {
        return value != null && value.isBoolean() && value.booleanValue();
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
}
