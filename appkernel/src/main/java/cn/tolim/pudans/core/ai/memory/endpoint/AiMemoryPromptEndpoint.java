package cn.tolim.pudans.core.ai.memory.endpoint;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.AiMemoryPrompt;
import cn.tolim.pudans.contract.ai.resource.AiMemoryPromptBinding;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.ResolvedMemoryPrompt;
import cn.tolim.pudans.core.ai.memory.prompt.MemoryPromptLog;
import cn.tolim.pudans.core.ai.memory.prompt.MemoryPromptSettings;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiMemoryPromptEndpoint implements CustomEndpoint {

    private static final String TAG = "AiMemoryPromptV1alpha1";

    private final ReactiveExtensionClient client;

    private final MemoryPromptResolver resolver;

    private final MemoryPromptSettings settingService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aimemoryprompts/-/resolve", this::resolve, builder -> builder
                        .operationId("resolveAiMemoryPrompt")
                        .description("Resolve the effective memory prompt for a layer, team and agent.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("layer").required(true))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("agent").required(false))
                        .response(responseBuilder().implementation(ResolvedMemoryPrompt.class)))
                .GET("/aimemoryprompts/-/settings", this::settings, builder -> builder
                        .operationId("getAiMemoryPromptSettings")
                        .description("Get the effective memory prompt settings for a team and agent.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("layer").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("team").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("agent").required(false))
                        .response(responseBuilder().implementation(ResolvedMemoryPrompt.class)))
                .POST("/aimemoryprompts/-/set", this::set, builder -> builder
                        .operationId("setAiMemoryPromptBinding")
                        .description("Apply or clear a memory prompt binding and record an operation log.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(SetRequest.class))
                        .response(responseBuilder().implementation(MemoryPromptLog.class)))
                .GET("/aimemoryprompts/-/log", this::log, builder -> builder
                        .operationId("listAiMemoryPromptLogs")
                        .description("List memory prompt operation logs.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("promptId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("from").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("to").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false))
                        .response(responseBuilder().implementationArray(MemoryPromptLog.class)))
                .build();
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        var layer = layer(request.queryParam("layer").orElse(null));
        var team = request.queryParam("team").orElse(null);
        var agent = request.queryParam("agent").orElse(null);
        return Mono.zip(
                        client.listAll(AiMemoryPromptBinding.class, ListOptions.builder().build(), defaultSort())
                                .collectList(),
                        client.listAll(AiMemoryPrompt.class, ListOptions.builder().build(), defaultSort())
                                .collectList())
                .map(tuple -> resolver.resolve(layer, team, agent, tuple.getT1(), tuple.getT2()))
                .flatMap(resolved -> ServerResponse.ok().bodyValue(resolved));
    }

    private Mono<ServerResponse> settings(ServerRequest request) {
        var team = query(request, "team");
        var agent = query(request, "agent");
        var layer = optionalLayer(query(request, "layer"));
        if (layer == null) {
            return settingService
                    .settings(team, agent)
                    .flatMap(settings -> ServerResponse.ok().bodyValue(settings));
        }
        return settingService
                .settings(layer, team, agent)
                .flatMap(resolved -> ServerResponse.ok().bodyValue(resolved));
    }

    private Mono<ServerResponse> set(ServerRequest request) {
        return request
                .bodyToMono(SetRequest.class)
                .switchIfEmpty(Mono.error(invalid("请求体不能为空。")))
                .flatMap(body -> requests
                        .userId(request)
                        .flatMap(actor -> settingService.set(actor, new MemoryPromptSettings.SetCommand(
                                body.name(),
                                body.layer(),
                                body.targetType(),
                                body.team(),
                                body.agent(),
                                body.action()))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> log(ServerRequest request) {
        var promptId = query(request, "promptId");
        var from = instantParam(request, "from");
        var to = instantParam(request, "to");
        var beforeId = AiEndpointRequests.longParam(request, "beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE);
        var limit = AiEndpointRequests.intParam(request, "limit", 50, 1, 200);
        return ServerResponse.ok()
                .body(settingService.logs(promptId, from, to, beforeId, limit), MemoryPromptLog.class);
    }

    private static AiMemoryPrompt.Layer layer(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServerWebInputException("layer 不能为空。");
        }
        return optionalLayer(value);
    }

    private static AiMemoryPrompt.@Nullable Layer optionalLayer(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AiMemoryPrompt.Layer.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ServerWebInputException("layer 必须是 L1、L2 或 L3。");
        }
    }

    private static @Nullable String query(ServerRequest request, String name) {
        return request.queryParam(name).filter(StringUtils::hasText).orElse(null);
    }

    private static @Nullable Instant instantParam(ServerRequest request, String name) {
        var value = query(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw invalid(name + " 时间格式不正确。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, MemoryPromptSettings.CODE_INVALID, message);
    }

    public record SetRequest(
            @Nullable String name,
            AiMemoryPrompt.@Nullable Layer layer,
            AiMemoryPromptBinding.@Nullable TargetType targetType,
            @Nullable String team,
            @Nullable String agent,
            MemoryPromptLog.@Nullable Action action) {}
}