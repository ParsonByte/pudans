package cn.tolim.pudans.core.ai.library.mcp.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.mcp.McpServerService;
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
public class AiMcpServerEndpoint implements CustomEndpoint {

    private static final String TAG = "AiMcpServerV1alpha1";

    private final McpServerService mcpServerService;

    private final AiTeamService teamService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aimcpservers/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiMcpServersByAgent")
                        .description("List enabled MCP servers bound to an agent with their validated tools.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder()
                                .implementationArray(McpServerService.McpServerView.class)))
                .POST("/aimcpservers/{name}/test", this::test, builder -> builder
                        .operationId("testAiMcpServer")
                        .description("Probe an MCP server, update its health status and return its tools.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .response(responseBuilder()
                                .implementation(McpServerService.TestResult.class)))
                .build();
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> teamService.requireAgent(agentName, userId))
                .flatMapMany(agent -> mcpServerService.byAgent(agent.getMetadata().getName()))
                .collectList()
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    private Mono<ServerResponse> test(ServerRequest request) {
        return requests
                .userId(request)
                .then(Mono.defer(() -> mcpServerService.test(request.pathVariable("name"))))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static String agent(ServerRequest request) {
        return request.queryParam("agent")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 'agent' 不能为空。"));
    }

    private static Builder agentParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("agent").required(true);
    }

    private static Builder nameParameter() {
        return parameterBuilder().in(ParameterIn.PATH).name("name").required(true);
    }
}