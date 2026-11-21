package cn.tolim.pudans.core.ai.library.instruction.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.ai.resource.AiInstruction;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.instruction.Instructions;
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
public class AiInstructionEndpoint implements CustomEndpoint {

    private static final String TAG = "AiInstructionV1alpha1";

    private final Instructions instructionService;

    private final Teams teams;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiinstructions/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiInstructionsByAgent")
                        .description("List shared instructions and agent-bound instructions for an agent.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementationArray(AiInstruction.class)))
                .build();
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> teams.requireAgent(agentName, userId))
                .flatMapMany(agent -> instructionService.byAgent(agent.getMetadata().getName()))
                .collectList()
                .flatMap(instructions -> ServerResponse.ok().bodyValue(instructions));
    }

    private static String agent(ServerRequest request) {
        return request.queryParam("agent")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 'agent' 不能为空。"));
    }

    private static Builder agentParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("agent").required(true);
    }
}