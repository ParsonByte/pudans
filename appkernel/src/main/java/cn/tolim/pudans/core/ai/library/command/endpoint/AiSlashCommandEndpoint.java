package cn.tolim.pudans.core.ai.library.command.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiSlashCommand;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.command.SlashCommandService;
import cn.tolim.pudans.core.ai.library.command.SlashCommandService.ResolvedSlashCommand;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
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
public class AiSlashCommandEndpoint implements CustomEndpoint {

    private static final String TAG = "AiSlashCommandV1alpha1";

    private final SlashCommandService slashCommandService;

    private final AiEndpointRequests requests;

    private final AiTeamService teamService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aislashcommands/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiSlashCommandsByAgent")
                        .description("List enabled slash commands bound to an agent.")
                        .tag(TAG)
                        .parameter(queryParameter("agent", "metadata.name of the agent."))
                        .response(responseBuilder().implementationArray(AiSlashCommand.class)))
                .POST("/aislashcommands/-/resolve", this::resolve, builder -> builder
                        .operationId("resolveAiSlashCommand")
                        .description("Expand a slash command for an agent.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(ResolveRequest.class))
                        .response(responseBuilder().implementation(ResolvedSlashCommand.class)))
                .build();
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = requiredQuery(request, "agent");
        return requests
                .userId(request)
                .flatMapMany(userId -> teamService
                        .requireAgent(agentName, userId)
                        .thenMany(slashCommandService.byAgent(agentName)))
                .collectList()
                .flatMap(commands -> ServerResponse.ok().bodyValue(commands));
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        return request.bodyToMono(ResolveRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> {
                    if (!StringUtils.hasText(body.agent())) {
                        return Mono.error(new ServerWebInputException("agent 不能为空。"));
                    }
                    if (!StringUtils.hasText(body.text())) {
                        return Mono.error(new ServerWebInputException("text 不能为空。"));
                    }
                    return requests
                            .userId(request)
                            .flatMap(userId -> teamService
                                    .requireAgent(body.agent(), userId)
                                    .then(slashCommandService.resolve(body.agent(), body.text())))
                            .flatMap(resolved -> ServerResponse.ok().bodyValue(resolved));
                });
    }

    private static String requiredQuery(ServerRequest request, String name) {
        return request.queryParam(name)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 '" + name + "' 不能为空。"));
    }

    private static Builder queryParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(true).description(description);
    }

    public record ResolveRequest(String agent, String text) {}
}