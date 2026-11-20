package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.plan.PlanUsageService;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiPlanUsageEndpoint implements CustomEndpoint {

    private static final String TAG = "AiPlanV1alpha1";

    private final PlanUsageService usageService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiplan/-/usage", this::currentUser, builder -> builder
                        .operationId("getAiPlanUsage")
                        .description("Get the current user's plan windows and reset voucher totals.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(PlanUsageService.UsageView.class)))
                .GET("/aiplan/-/usage/{uid}", this::userUsage, builder -> builder
                        .operationId("getAiPlanUsageByUser")
                        .description("Get a specific user's plan windows and reset voucher totals.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(PlanUsageService.UsageView.class)))
                .build();
    }

    private Mono<ServerResponse> currentUser(ServerRequest request) {
        return requests.userId(request)
                .flatMap(usageService::usage)
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> userUsage(ServerRequest request) {
        return usageService
                .usage(request.pathVariable("uid"))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }
}