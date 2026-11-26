package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.dto.AiImageContextResult;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiLegacyImageRequest;
import cn.tolim.pudans.contract.ai.dto.AiLegacyImageResult;
import cn.tolim.pudans.contract.ai.dto.AiLegacyVideoRequest;
import cn.tolim.pudans.contract.ai.dto.AiLegacyVideoTask;
import cn.tolim.pudans.contract.ai.dto.AiLyricsGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiLyricsGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiMusicCoverRequest;
import cn.tolim.pudans.contract.ai.dto.AiMusicCoverResult;
import cn.tolim.pudans.contract.ai.dto.AiMusicGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiMusicGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationPage;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoRegenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskDeleted;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskSubmitted;
import cn.tolim.pudans.contract.ai.dto.AiVoiceCloneRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceCloneResult;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDeleteRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDeletion;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDesignRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDesignResult;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechAsyncRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechTask;
import cn.tolim.pudans.contract.ai.dto.AiSpeechTaskSubmitted;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListResult;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxGateway;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxProvider;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiMiniMaxCapabilityEndpoint implements CustomEndpoint {

    private static final String TAG = "AiMiniMaxCapabilityV1alpha1";

    private final AiChannelResolver resolver;

    private final MiniMaxGateway gateway;

    private final MiniMaxProvider provider;

    // 注册 MiniMax 图像、视频、音乐与音色能力路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aichannels/{name}/-/minimax/image-generations", this::imageGenerations, builder -> builder
                        .operationId("createMiniMaxImageGeneration")
                        .description("Generate images through the MiniMax Images API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiImageGenerationRequest.class))
                        .response(responseBuilder().implementation(AiImageGenerationResult.class)))
                .POST("/aichannels/{name}/-/minimax/image-edits", this::imageEdits, builder -> builder
                        .operationId("createMiniMaxImageEdit")
                        .description("Edit images through the MiniMax Images API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiImageGenerationRequest.class))
                        .response(responseBuilder().implementation(AiImageGenerationResult.class)))
                .POST("/aichannels/{name}/-/minimax/image-context-ir", this::imageContext, builder -> builder
                        .operationId("createMiniMaxImageContext")
                        .description("Enhance an image prompt through the MiniMax Image Context API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiImageGenerationRequest.class))
                        .response(responseBuilder().implementation(AiImageContextResult.class)))
                .POST(
                        "/aichannels/{name}/-/minimax/legacy-image-generations",
                        this::legacyImageGenerations,
                        builder -> builder
                        .operationId("createMiniMaxLegacyImageGeneration")
                        .description("Generate images through the legacy MiniMax Images API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiLegacyImageRequest.class))
                        .response(responseBuilder().implementation(AiLegacyImageResult.class)))
                .POST(
                        "/aichannels/{name}/-/minimax/legacy-video-generations",
                        this::legacyVideoGenerations,
                        builder -> builder
                        .operationId("createMiniMaxLegacyVideoGeneration")
                        .description("Create a legacy MiniMax video generation task.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiLegacyVideoRequest.class))
                        .response(responseBuilder().implementation(AiVideoTaskSubmitted.class)))
                .GET(
                        "/aichannels/{name}/-/minimax/legacy-video-generations/{taskId}",
                        this::legacyVideoTask,
                        builder -> builder
                        .operationId("getMiniMaxLegacyVideoGeneration")
                        .description("Get a legacy MiniMax video generation task.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiLegacyVideoTask.class)))
                .POST(
                        "/aichannels/{name}/-/minimax/video-agent-generations",
                        this::videoAgentGenerations,
                        builder -> builder
                        .operationId("createMiniMaxVideoAgentGeneration")
                        .description("Create a MiniMax video agent task from a template.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVideoAgentRequest.class))
                        .response(responseBuilder().implementation(AiVideoTaskSubmitted.class)))
                .GET(
                        "/aichannels/{name}/-/minimax/video-agent-generations/{taskId}",
                        this::videoAgentTask,
                        builder -> builder
                        .operationId("getMiniMaxVideoAgentGeneration")
                        .description("Get a MiniMax video agent task.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiVideoAgentTask.class)))
                .POST("/aichannels/{name}/-/minimax/video-generations", this::videoGenerations, builder -> builder
                        .operationId("createMiniMaxVideoGeneration")
                        .description("Create a MiniMax video generation task.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVideoGenerationRequest.class))
                        .response(responseBuilder().implementation(AiVideoTaskSubmitted.class)))
                .GET(
                        "/aichannels/{name}/-/minimax/video-generations/{taskId}",
                        this::videoGeneration,
                        builder -> builder
                        .operationId("getMiniMaxVideoGeneration")
                        .description("Get a MiniMax video generation task.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiVideoGenerationTask.class)))
                .GET(
                        "/aichannels/{name}/-/minimax/video-generations",
                        this::videoGenerationsPage,
                        builder -> builder
                        .operationId("listMiniMaxVideoGenerations")
                        .description("List MiniMax video generation tasks.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiVideoGenerationPage.class)))
                .DELETE(
                        "/aichannels/{name}/-/minimax/video-generations/{taskId}",
                        this::deleteVideoGeneration,
                        builder -> builder
                        .operationId("deleteMiniMaxVideoGeneration")
                        .description("Cancel or delete a MiniMax video generation task.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiVideoTaskDeleted.class)))
                .POST("/aichannels/{name}/-/minimax/video-context-ir", this::videoContext, builder -> builder
                        .operationId("createMiniMaxVideoContext")
                        .description("Create a MiniMax video context enhancement task.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVideoGenerationRequest.class))
                        .response(responseBuilder().implementation(AiVideoTaskSubmitted.class)))
                .POST("/aichannels/{name}/-/minimax/video-regenerations", this::videoRegenerations, builder -> builder
                        .operationId("createMiniMaxVideoRegeneration")
                        .description("Create a MiniMax video regeneration task.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVideoRegenerationRequest.class))
                        .response(responseBuilder().implementation(AiVideoTaskSubmitted.class)))
                .POST("/aichannels/{name}/-/minimax/speech-async-tasks", this::speechAsyncCreate, builder -> builder
                        .operationId("createMiniMaxSpeechAsyncTask")
                        .description("Create a MiniMax asynchronous speech synthesis task.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiSpeechAsyncRequest.class))
                        .response(responseBuilder().implementation(AiSpeechTaskSubmitted.class)))
                .GET(
                        "/aichannels/{name}/-/minimax/speech-async-tasks/{taskId}",
                        this::speechAsyncTask,
                        builder -> builder
                        .operationId("getMiniMaxSpeechAsyncTask")
                        .description("Get a MiniMax asynchronous speech synthesis task.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiSpeechTask.class)))
                .POST("/aichannels/{name}/-/minimax/music-generations", this::musicGenerations, builder -> builder
                        .operationId("createMiniMaxMusicGeneration")
                        .description("Generate music through the MiniMax Music API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiMusicGenerationRequest.class))
                        .response(responseBuilder().implementation(AiMusicGenerationResult.class)))
                .POST(
                        "/aichannels/{name}/-/minimax/music-cover-preprocess",
                        this::musicCover,
                        builder -> builder
                        .operationId("createMiniMaxMusicCoverPreprocess")
                        .description("Preprocess a reference audio for MiniMax music cover generation.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiMusicCoverRequest.class))
                        .response(responseBuilder().implementation(AiMusicCoverResult.class)))
                .POST("/aichannels/{name}/-/minimax/lyrics-generations", this::lyricsGenerations, builder -> builder
                        .operationId("createMiniMaxLyricsGeneration")
                        .description("Generate lyrics through the MiniMax Lyrics API.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiLyricsGenerationRequest.class))
                        .response(responseBuilder().implementation(AiLyricsGenerationResult.class)))
                .POST(
                        "/aichannels/{name}/-/minimax/responses-input-tokens",
                        this::responsesInputTokens,
                        builder -> builder
                        .operationId("estimateMiniMaxInputTokens")
                        .description("Estimate MiniMax Responses API input tokens with a raw JSON body.")
                        .tag(TAG)
                        .requestBody(jsonObjectRequest())
                        .response(responseBuilder().responseCode("200").description("Provider JSON")))
                .POST("/aichannels/{name}/-/minimax/voice-list", this::voiceList, builder -> builder
                        .operationId("listMiniMaxVoices")
                        .description("List MiniMax voices.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVoiceListRequest.class))
                        .response(responseBuilder().implementation(AiVoiceListResult.class)))
                .POST("/aichannels/{name}/-/minimax/voice-delete", this::voiceDelete, builder -> builder
                        .operationId("deleteMiniMaxVoice")
                        .description("Delete a MiniMax cloned or generated voice.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVoiceDeleteRequest.class))
                        .response(responseBuilder().implementation(AiVoiceDeletion.class)))
                .POST("/aichannels/{name}/-/minimax/voice-clone", this::voiceClone, builder -> builder
                        .operationId("cloneMiniMaxVoice")
                        .description("Clone a MiniMax voice from an uploaded audio file.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVoiceCloneRequest.class))
                        .response(responseBuilder().implementation(AiVoiceCloneResult.class)))
                .POST("/aichannels/{name}/-/minimax/voice-design", this::voiceDesign, builder -> builder
                        .operationId("designMiniMaxVoice")
                        .description("Design a MiniMax voice from a prompt.")
                        .tag(TAG)
                        .requestBody(jsonRequest(AiVoiceDesignRequest.class))
                        .response(responseBuilder().implementation(AiVoiceDesignResult.class)))
                .build();
    }

    private Mono<ServerResponse> imageGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiImageGenerationRequest.class)
                .flatMap(body -> Boolean.TRUE.equals(body.stream())
                        ? serverSentEvents(gateway.imageGenerationsStream(
                                resolved.channel(), resolved.credentials(), body))
                        : gateway
                                .imageGenerations(resolved.channel(), resolved.credentials(), body)
                                .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> imageEdits(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiImageGenerationRequest.class)
                .flatMap(body -> Boolean.TRUE.equals(body.stream())
                        ? serverSentEvents(gateway.imageEditsStream(
                                resolved.channel(), resolved.credentials(), body))
                        : gateway
                                .imageEdits(resolved.channel(), resolved.credentials(), body)
                                .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> imageContext(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiImageGenerationRequest.class)
                .flatMap(body -> gateway
                        .imageContext(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> legacyImageGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiLegacyImageRequest.class)
                .flatMap(body -> gateway
                        .legacyImageGeneration(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> legacyVideoGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiLegacyVideoRequest.class)
                .flatMap(body -> gateway
                        .legacyVideoGeneration(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> legacyVideoTask(ServerRequest request) {
        return channel(request).flatMap(resolved -> gateway
                .legacyVideoTask(
                        resolved.channel(),
                        resolved.credentials(),
                        request.pathVariable("taskId"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> videoAgentGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVideoAgentRequest.class)
                .flatMap(body -> gateway
                        .videoAgentGeneration(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> videoAgentTask(ServerRequest request) {
        return channel(request).flatMap(resolved -> gateway
                .videoAgentTask(
                        resolved.channel(),
                        resolved.credentials(),
                        request.pathVariable("taskId"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> videoGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVideoGenerationRequest.class)
                .flatMap(body -> gateway
                        .videoGeneration(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> videoGeneration(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> gateway
                        .videoTask(
                                resolved.channel(),
                                resolved.credentials(),
                                request.pathVariable("taskId"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> videoGenerationsPage(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> gateway
                        .videoTasks(
                                resolved.channel(),
                                resolved.credentials(),
                                queryInteger(request, "page_num"),
                                queryInteger(request, "page_size"),
                                request.queryParam("status").orElse(null),
                                request.queryParams().get("task_ids"),
                                request.queryParam("model").orElse(null),
                                request.queryParam("task_type").orElse(null))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> deleteVideoGeneration(ServerRequest request) {
        return channel(request)
                .flatMap(resolved -> gateway
                        .deleteVideoTask(
                                resolved.channel(),
                                resolved.credentials(),
                                request.pathVariable("taskId"))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> videoContext(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVideoGenerationRequest.class)
                .flatMap(body -> gateway
                        .videoContext(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> videoRegenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVideoRegenerationRequest.class)
                .flatMap(body -> gateway
                        .videoRegeneration(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> musicGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiMusicGenerationRequest.class)
                .flatMap(body -> Boolean.TRUE.equals(body.stream())
                        ? serverSentEvents(gateway.musicGenerationStream(
                                resolved.channel(), resolved.credentials(), body))
                        : gateway
                                .musicGeneration(resolved.channel(), resolved.credentials(), body)
                                .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> lyricsGenerations(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiLyricsGenerationRequest.class)
                .flatMap(body -> gateway
                        .lyricsGeneration(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> speechAsyncCreate(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiSpeechAsyncRequest.class)
                .flatMap(body -> gateway
                        .speechAsyncCreate(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> speechAsyncTask(ServerRequest request) {
        return channel(request).flatMap(resolved -> gateway
                .speechAsyncTask(
                        resolved.channel(),
                        resolved.credentials(),
                        request.pathVariable("taskId"))
                .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> musicCover(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiMusicCoverRequest.class)
                .flatMap(body -> gateway
                        .musicCover(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> responsesInputTokens(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonMap(request)
                .flatMap(body -> gateway
                        .responsesInputTokens(resolved.channel(), resolved.credentials(), body)
                        .flatMap(AiProviderRawJson::response)));
    }

    private Mono<ServerResponse> voiceList(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVoiceListRequest.class)
                .flatMap(body -> gateway
                        .voiceList(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> voiceDelete(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVoiceDeleteRequest.class)
                .flatMap(body -> gateway
                        .voiceDelete(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> voiceClone(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVoiceCloneRequest.class)
                .flatMap(body -> gateway
                        .voiceClone(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> voiceDesign(ServerRequest request) {
        return channel(request).flatMap(resolved -> jsonBody(request, AiVoiceDesignRequest.class)
                .flatMap(body -> gateway
                        .voiceDesign(resolved.channel(), resolved.credentials(), body)
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<AiChannels.ResolvedChannel> channel(
            ServerRequest request) {
        return resolver.require(request, provider.descriptor().provider());
    }

    private static Mono<ServerResponse> serverSentEvents(Flux<ServerSentEvent<String>> events) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(events, new ParameterizedTypeReference<ServerSentEvent<String>>() {});
    }

    private static <T> Mono<T> jsonBody(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type).switchIfEmpty(Mono.error(invalid("请求体不能为空。")));
    }

    private static Mono<Map<String, Object>> jsonMap(ServerRequest request) {
        return request
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")));
    }

    private static Integer queryInteger(ServerRequest request, String name) {
        var value = request.queryParam(name).orElse(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw invalid(name + " 必须是整数。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "ai-provider-request-invalid", message);
    }

    private static org.springdoc.core.fn.builders.requestbody.Builder jsonObjectRequest() {
        return requestBodyBuilder()
                .required(true)
                .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE));
    }

    private static org.springdoc.core.fn.builders.requestbody.Builder jsonRequest(Class<?> type) {
        return requestBodyBuilder()
                .required(true)
                .content(contentBuilder()
                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                        .schema(schemaBuilder().implementation(type)));
    }
}
