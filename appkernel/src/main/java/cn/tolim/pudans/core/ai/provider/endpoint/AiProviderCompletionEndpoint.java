package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.provider.AiCompletionChunk;
import cn.tolim.pudans.core.ai.provider.AiCompletionRequest;
import cn.tolim.pudans.core.ai.provider.AiCompletionResult;
import cn.tolim.pudans.core.ai.provider.AiProviderJsonSupport;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekCompletionClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProtocol;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderCompletionEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderV1alpha1";

    private static final String CODE_INVALID = "ai-provider-completion-invalid";

    private static final String DONE = "data: [DONE]\n\n";

    private final DeepSeekProviderAccess access;

    private final DeepSeekCompletionClient completionClient;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiproviders/{name}/completions", this::complete, builder -> builder
                        .operationId("createAiProviderCompletion")
                        .description("Create a fill-in-the-middle completion with the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(CompletionRequest.class))
                        .response(responseBuilder().implementation(AiCompletionResult.class)))
                .POST("/aiproviders/{name}/completions/stream", this::stream, builder -> builder
                        .operationId("streamAiProviderCompletion")
                        .description("Stream a fill-in-the-middle completion with the provider.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider."))
                        .requestBody(requestBodyBuilder().required(true).implementation(CompletionRequest.class))
                        .response(responseBuilder().content(
                                org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.TEXT_EVENT_STREAM_VALUE))))
                .build();
    }

    private Mono<ServerResponse> complete(ServerRequest request) {
        return prepare(request)
                .flatMap(prepared -> completionClient
                        .complete(prepared.provider(), prepared.account(), prepared.model(), prepared.request())
                        .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> stream(ServerRequest request) {
        return prepare(request).flatMap(prepared -> {
            var body = completionClient
                    .stream(prepared.provider(), prepared.account(), prepared.model(), prepared.request())
                    .map(this::event)
                    .concatWith(Flux.just(DONE))
                    .onErrorResume(error -> Flux.just(errorEvent(error)));
            return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body, String.class);
        });
    }

    private Mono<Prepared> prepare(ServerRequest request) {
        return access.requireProvider(request.pathVariable("name"))
                .flatMap(provider -> request.bodyToMono(CompletionRequest.class)
                        .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                        .flatMap(body -> {
                            if (!StringUtils.hasText(body.prompt())) {
                                return Mono.error(invalid("prompt 不能为空。"));
                            }
                            return access.requireChatModel(body.model(), provider)
                                    .zipWith(access.requireAccount(provider))
                                    .map(tuple -> new Prepared(
                                            provider,
                                            tuple.getT2(),
                                            tuple.getT1(),
                                            new AiCompletionRequest(
                                                    body.prompt(),
                                                    body.suffix(),
                                                    body.maxTokens(),
                                                    body.temperature(),
                                                    body.topP(),
                                                    body.stop(),
                                                    body.echo(),
                                                    body.logprobs())));
                        }));
    }

    private String event(AiCompletionChunk chunk) {
        return "data: " + AiProviderJsonSupport.write(
                mapper, mapper.valueToTree(chunk), DeepSeekProtocol.NAME) + "\n\n";
    }

    private String errorEvent(Throwable error) {
        var reason = error instanceof ApiException apiException && apiException.getReason() != null
                ? apiException.getReason()
                : "补全失败。";
        var node = mapper.createObjectNode();
        node.putObject("error").put("message", reason);
        return "data: " + AiProviderJsonSupport.write(mapper, node, DeepSeekProtocol.NAME) + "\n\n";
    }

    private static ApiException invalid(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, reason);
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }

    public record CompletionRequest(
            String model,
            String prompt,
            String suffix,
            Integer maxTokens,
            Double temperature,
            Double topP,
            List<String> stop,
            Boolean echo,
            Integer logprobs) {}

    private record Prepared(
            AiProvider provider, AiProviderAccount account, AiModel model, AiCompletionRequest request) {}
}
