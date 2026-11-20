package cn.tolim.pudans.core.ai.library.environment.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariableService;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariableService.MaskedEnvironmentVariable;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiEnvironmentVariableEndpoint implements CustomEndpoint {

    private static final String TAG = "AiEnvironmentVariableV1alpha1";

    private final EnvironmentVariableService environmentVariableService;

    private final AiEndpointRequests requests;

    private final AiTeamService teamService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aienvironmentvariables/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiEnvironmentVariablesByAgent")
                        .description("List masked environment variables bound to an agent.")
                        .tag(TAG)
                        .parameter(queryParameter("agent", "metadata.name of the agent."))
                        .response(responseBuilder().implementationArray(MaskedEnvironmentVariable.class)))
                .POST("/aienvironmentvariables/{name}/value", this::updateValue, builder -> builder
                        .operationId("updateAiEnvironmentVariableValue")
                        .description("Set or clear the write-only value of an environment variable.")
                        .tag(TAG)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.PATH)
                                .name("name")
                                .required(true)
                                .description("metadata.name of the environment variable."))
                        .requestBody(requestBodyBuilder().required(true).implementation(ValueUpdateRequest.class))
                        .response(responseBuilder().implementation(MaskedEnvironmentVariable.class)))
                .build();
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = requiredQuery(request, "agent");
        return requests
                .userId(request)
                .flatMapMany(userId -> teamService
                        .requireAgent(agentName, userId)
                        .thenMany(environmentVariableService.maskedByAgent(agentName)))
                .collectList()
                .flatMap(variables -> ServerResponse.ok().bodyValue(variables));
    }

    private Mono<ServerResponse> updateValue(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(ValueUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> environmentVariableService.updateValue(
                        name, body.value(), Boolean.TRUE.equals(body.clear())))
                .map(EnvironmentVariableService::mask)
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private static String requiredQuery(ServerRequest request, String name) {
        return request.queryParam(name)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 '" + name + "' 不能为空。"));
    }

    private static Builder queryParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(true).description(description);
    }

    public record ValueUpdateRequest(@Nullable String value, @Nullable Boolean clear) {}
}