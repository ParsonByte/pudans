package cn.tolim.pudans.core.ai.generation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.generation.DocumentGenerations;
import cn.tolim.pudans.core.ai.generation.AiGenerationCommand;
import cn.tolim.pudans.core.ai.generation.AiGenerationResult;
import cn.tolim.pudans.core.ai.generation.Generations;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiGenerationEndpoint implements CustomEndpoint {

    private static final String TAG = "AiGenerationV1alpha1";

    private static final String CODE_NOT_FOUND = "ai-generation-not-found";

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private final Generations generations;

    private final DocumentGenerations documentGenerations;

    private final AiModelRequestRepository requestRepository;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aigenerations", this::generate, builder -> builder
                        .operationId("createAiGeneration")
                        .description("Generate text, image, video, speech, audio, music or 3D content.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(GenerationBody.class))
                        .response(responseBuilder().implementation(AiGenerationResult.class)))
                .GET("/aigenerations", this::list, builder -> builder
                        .operationId("listAiGenerations")
                        .description("List the current user's generation requests.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(AiGenerationResult.class)))
                .POST("/aigenerations/-/documents", this::generateDocument, builder -> builder
                        .operationId("createAiDocumentGeneration")
                        .description("Generate a Markdown or HTML document and store it as a file.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(DocumentBody.class))
                        .response(responseBuilder().implementation(DocumentGenerations.Result.class)))
                .GET("/aigenerations/{requestId}", this::get, builder -> builder
                        .operationId("getAiGeneration")
                        .description("Get a generation request and its result.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("requestId").required(true))
                        .response(responseBuilder().implementation(AiGenerationResult.class)))
                .build();
    }

    private Mono<ServerResponse> generate(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(userId -> request.bodyToMono(GenerationBody.class)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, "ai-generation-body-invalid", "请求体不能为空。")))
                        .flatMap(body -> generations.generate(command(userId, body))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> generateDocument(ServerRequest request) {
        return requests
                .userId(request)
                .flatMap(userId -> request
                        .bodyToMono(DocumentBody.class)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "ai-document-generation-invalid",
                                "请求体不能为空。")))
                        .flatMap(body -> documentGenerations.generate(new DocumentGenerations.Command(
                                userId,
                                body.title(),
                                body.prompt(),
                                body.format(),
                                body.routeGroup()))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        var requestId = request.pathVariable("requestId");
        return requests
                .userId(request)
                .flatMap(userId -> requestRepository
                        .findByRequestIdAndUser(requestId, userId)
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.NOT_FOUND, CODE_NOT_FOUND, "生成请求不存在。"))))
                .flatMap(row -> ServerResponse.ok().bodyValue(AiGenerationResult.from(row)));
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        return requests
                .userId(request)
                .flatMap(userId -> requestRepository
                        .listByUser(userId, limit)
                        .map(AiGenerationResult::from)
                        .collectList())
                .flatMap(results -> ServerResponse.ok().bodyValue(results));
    }

    private static AiGenerationCommand command(String userId, GenerationBody body) {
        return new AiGenerationCommand(
                userId,
                body.model(),
                body.routeGroup(),
                body.capability(),
                body.inputs(),
                body.params(),
                body.idempotencyKey(),
                body.teamId(),
                body.agentId(),
                body.sessionId());
    }

    public record DocumentBody(
            @Nullable String title,
            String prompt,
            DocumentGenerations.@Nullable Format format,
            @Nullable String routeGroup) {}

    public record GenerationBody(
            @Nullable String model,
            @Nullable String routeGroup,
            AiModel.Capability capability,
            List<AiModelContent> inputs,
            Map<String, JsonNode> params,
            @Nullable String idempotencyKey,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String sessionId) {}
}
