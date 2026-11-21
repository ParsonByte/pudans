package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.plan.PlanUsage;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver;
import cn.tolim.pudans.infra.exception.ApiException;
import org.springframework.http.HttpStatus;
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

    private final PlanUsage planUsage;

    private final AiEndpointRequests requests;

    private final UsageAccessResolver accessResolver;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiplan/-/usage", this::currentUser, builder -> builder
                        .operationId("getAiPlanUsage")
                        .description("Get the current user's plan windows and reset voucher totals.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(PlanUsage.UsageView.class)))
                .GET("/aiplan/-/usage/{uid}", this::userUsage, builder -> builder
                        .operationId("getAiPlanUsageByUser")
                        .description("Get a specific user's plan windows and reset voucher totals.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(PlanUsage.UsageView.class)))
                .build();
    }

    private Mono<ServerResponse> currentUser(ServerRequest request) {
        return requests.userId(request)
                .flatMap(planUsage::usage)
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> userUsage(ServerRequest request) {
        var uid = request.pathVariable("uid");
        return accessResolver.viewer(request).flatMap(viewer -> {
            if (!uid.equals(viewer.userId()) && !viewer.admin()) {
                return Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ai-plan-usage-forbidden",
                        "无权查看其他用户的套餐用量。"));
            }
            return planUsage.usage(uid).flatMap(view -> ServerResponse.ok().bodyValue(view));
        });
    }
}