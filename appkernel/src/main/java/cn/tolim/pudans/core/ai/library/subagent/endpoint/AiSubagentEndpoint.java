package cn.tolim.pudans.core.ai.library.subagent.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.ai.resource.AiSubagent;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.subagent.Subagents;
import cn.tolim.pudans.core.ai.team.Teams;
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
public class AiSubagentEndpoint implements CustomEndpoint {

    private static final String TAG = "AiSubagentV1alpha1";

    private final Subagents subagentService;

    private final AiEndpointRequests requests;

    private final Teams teams;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aisubagents/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiSubagentsByAgent")
                        .description("List enabled subagents bound to an agent.")
                        .tag(TAG)
                        .parameter(queryParameter("agent", "metadata.name of the agent."))
                        .response(responseBuilder().implementationArray(AiSubagent.class)))
                .build();
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = requiredQuery(request, "agent");
        return requests
                .userId(request)
                .flatMapMany(userId -> teams
                        .requireAgent(agentName, userId)
                        .thenMany(subagentService.byAgent(agentName)))
                .collectList()
                .flatMap(subagents -> ServerResponse.ok().bodyValue(subagents));
    }

    private static String requiredQuery(ServerRequest request, String name) {
        return request.queryParam(name)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 '" + name + "' 不能为空。"));
    }

    private static Builder queryParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(true).description(description);
    }
}