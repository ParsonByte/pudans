package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekToolsClient;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderToolEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private static final String CODE_INVALID = "ai-provider-tools-invalid";

    private static final String CODE_FILE_NOT_FOUND = "ai-provider-tools-file-not-found";

    private static final String CODE_TOO_LARGE = "ai-provider-tools-file-too-large";

    private static final int MAX_OCR_BYTES = 100 * 1024 * 1024;

    private static final int MAX_ASR_BYTES = 8 * 1024 * 1024;

    private static final Set<String> OCR_PURPOSES =
            Set.of("text_recognition", "rich_layout_recognition");

    private static final Set<String> TTS_FORMATS = Set.of("wav", "pcm");

    private final DeepSeekProviderAccess access;

    private final DeepSeekToolsClient toolsClient;

    private final StorageService storageService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiproviders/{name}/tools/search", this::search, builder -> builder
                        .operationId("searchAiProviderWeb")
                        .description("Search the web with the provider tool API.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true)
                                .implementation(DeepSeekToolsClient.SearchRequest.class))
                        .response(responseBuilder().implementation(DeepSeekToolsClient.SearchResult.class)))
                .POST("/aiproviders/{name}/tools/fetch", this::fetch, builder -> builder
                        .operationId("fetchAiProviderWebPage")
                        .description("Fetch a web page with the provider tool API.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(FetchRequest.class))
                        .response(responseBuilder().implementation(DeepSeekToolsClient.FetchResult.class)))
                .POST("/aiproviders/{name}/tools/ocr", this::ocr, builder -> builder
                        .operationId("recognizeAiProviderDocument")
                        .description("Recognize a stored document with the provider OCR tool.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(OcrRequest.class))
                        .response(responseBuilder().implementation(DeepSeekToolsClient.OcrResult.class)))
                .POST("/aiproviders/{name}/tools/asr", this::asr, builder -> builder
                        .operationId("recognizeAiProviderAudio")
                        .description("Transcribe a stored audio file with the provider ASR tool.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(AsrRequest.class))
                        .response(responseBuilder().implementation(DeepSeekToolsClient.AsrResult.class)))
                .POST("/aiproviders/{name}/tools/tts", this::tts, builder -> builder
                        .operationId("synthesizeAiProviderSpeech")
                        .description("Synthesize speech with the provider TTS tool.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(TtsRequest.class))
                        .response(responseBuilder().responseCode("200").description("Audio stream")))
                .build();
    }

    private Mono<ServerResponse> search(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> request.bodyToMono(DeepSeekToolsClient.SearchRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> {
                            if (!StringUtils.hasText(body.query())) {
                                return Mono.error(invalid("query 不能为空。"));
                            }
                            return toolsClient.search(prepared.provider(), prepared.account(), body);
                        }))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> fetch(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> request.bodyToMono(FetchRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> {
                            if (!StringUtils.hasText(body.url())) {
                                return Mono.error(invalid("url 不能为空。"));
                            }
                            return toolsClient.fetch(prepared.provider(), prepared.account(), body.url());
                        }))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> ocr(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> request.bodyToMono(OcrRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> {
                            if (!OCR_PURPOSES.contains(body.purpose())) {
                                return Mono.error(invalid(
                                        "OCR 用途必须是 text_recognition 或 rich_layout_recognition。"));
                            }
                            return storage(body.storageFileId(), MAX_OCR_BYTES)
                                    .flatMap(stored -> toolsClient.ocr(
                                            prepared.provider(),
                                            prepared.account(),
                                            filename(stored.file()),
                                            stored.file().getSpec().getContentType(),
                                            stored.content(),
                                            body.purpose()));
                        }))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> asr(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> request.bodyToMono(AsrRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> storage(body.storageFileId(), MAX_ASR_BYTES)
                                .flatMap(stored -> toolsClient.asr(
                                        prepared.provider(),
                                        prepared.account(),
                                        filename(stored.file()),
                                        stored.file().getSpec().getContentType(),
                                        stored.content()))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> tts(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> request.bodyToMono(TtsRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> {
                            if (!StringUtils.hasText(body.text())) {
                                return Mono.error(invalid("text 不能为空。"));
                            }
                            var format = StringUtils.hasText(body.format()) ? body.format() : "wav";
                            if (!TTS_FORMATS.contains(format)) {
                                return Mono.error(invalid("语音格式必须是 wav 或 pcm。"));
                            }
                            var audio = toolsClient.tts(
                                    prepared.provider(),
                                    prepared.account(),
                                    body.text(),
                                    body.voiceId(),
                                    format,
                                    body.filterMarkdown());
                            return ServerResponse.ok()
                                    .contentType("pcm".equals(format)
                                            ? MediaType.parseMediaType("audio/pcm")
                                            : MediaType.parseMediaType("audio/wav"))
                                    .body(audio, DataBuffer.class);
                        }));
    }

    private Mono<Prepared> prepare(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> access.requireAccount(provider)
                        .map(account -> new Prepared(provider, account)));
    }

    private Mono<StoredFile> storage(String storageFileId, int maxBytes) {
        if (!StringUtils.hasText(storageFileId)) {
            return Mono.error(invalid("存储文件标识不能为空。"));
        }
        return storageService
                .getFile(storageFileId)
                .onErrorMap(ExtensionNotFoundException.class, error -> fileNotFound())
                .switchIfEmpty(Mono.error(fileNotFound()))
                .flatMap(file -> {
                    if (file.getSpec() == null || file.getSpec().getState() != StorageFileState.ACTIVE) {
                        return Mono.error(fileNotFound());
                    }
                    return storageService
                            .readBytes(file.getMetadata().getName(), maxBytes)
                            .onErrorMap(DataBufferLimitException.class, error -> tooLarge())
                            .filter(content -> content.length > 0)
                            .switchIfEmpty(Mono.error(invalid("存储文件内容为空。")))
                            .map(content -> new StoredFile(file, content));
                });
    }

    private static String filename(StorageFile file) {
        var originalName = file.getSpec().getOriginalName();
        return StringUtils.hasText(originalName) ? originalName : file.getMetadata().getName();
    }

    private static ApiException invalid(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, reason);
    }

    private static ApiException fileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_FILE_NOT_FOUND, "存储文件不存在或不可用。");
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, CODE_TOO_LARGE, "存储文件超过允许的大小。");
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }

    public record FetchRequest(String url) {}

    public record OcrRequest(String storageFileId, String purpose) {}

    public record AsrRequest(String storageFileId) {}

    public record TtsRequest(
            String text, String voiceId, String format, Boolean filterMarkdown) {}

    private record Prepared(AiProvider provider, AiProviderAccount account) {}

    private record StoredFile(StorageFile file, byte[] content) {}
}
