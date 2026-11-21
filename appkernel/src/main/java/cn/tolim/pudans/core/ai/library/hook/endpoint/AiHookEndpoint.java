package cn.tolim.pudans.core.ai.library.hook.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.ai.AiHook;
import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.hook.Hooks;
import cn.tolim.pudans.core.ai.team.Teams;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Locale;
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
public class AiHookEndpoint implements CustomEndpoint {

    private static final String TAG = "AiHookV1alpha1";

    private static final String EVENT_PARSE_MESSAGE =
            "event 必须是 SESSION_START、USER_PROMPT_SUBMIT、PRE_TOOL_USE、POST_TOOL_USE 或 SESSION_END。";

    private final Hooks hookService;

    private final AiEndpointRequests requests;

    private final Teams teams;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aihooks/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiHooksByAgent")
                        .description("List enabled hooks bound to an agent, optionally filtered by event.")
                        .tag(TAG)
                        .parameter(queryParameter("agent", "metadata.name of the agent.", true))
                        .parameter(queryParameter("event", "Hook event name.", false))
                        .response(responseBuilder().implementationArray(AiHook.class)))
                .build();
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = requiredQuery(request, "agent");
        var event = event(request);
        return requests
                .userId(request)
                .flatMapMany(userId -> teams
                        .requireAgent(agentName, userId)
                        .thenMany(hookService.byAgent(agentName, event)))
                .collectList()
                .flatMap(hooks -> ServerResponse.ok().bodyValue(hooks));
    }

    private static String requiredQuery(ServerRequest request, String name) {
        return request.queryParam(name)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 '" + name + "' 不能为空。"));
    }

    private static AiHook.@Nullable Event event(ServerRequest request) {
        var value = request.queryParam("event").filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return null;
        }
        try {
            return AiHook.Event.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ServerWebInputException(EVENT_PARSE_MESSAGE);
        }
    }

    private static Builder queryParameter(String name, String description, boolean required) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(required).description(description);
    }
}