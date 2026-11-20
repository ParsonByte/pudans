package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.plan.PlanBindingService;
import cn.tolim.pudans.core.ai.plan.PlanResolutionService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiPlanBindingEndpoint implements CustomEndpoint {

    private static final String TAG = "AiPlanBindingV1alpha1";

    private final PlanBindingService bindingService;

    private final PlanResolutionService resolutionService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiplanbindings/-/bulk", this::bulk, builder -> builder
                        .operationId("bulkBindAiPlanBindings")
                        .description("Assign a plan to users or user groups, replacing existing bindings by default.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .implementation(PlanBindingService.BulkBindRequest.class))
                        .response(responseBuilder().implementation(PlanBindingService.BulkBindResult.class)))
                .GET("/aiplanbindings/-/resolve/{uid}", this::resolve, builder -> builder
                        .operationId("resolveAiPlanBinding")
                        .description("Preview the plan that a user currently resolves to.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(ResolutionView.class)))
                .build();
    }

    private Mono<ServerResponse> bulk(ServerRequest request) {
        return request.bodyToMono(PlanBindingService.BulkBindRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(bindingService::bulk)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> resolve(ServerRequest request) {
        return resolutionService
                .resolve(request.pathVariable("uid"))
                .map(resolved -> new ResolutionView(
                        resolved.planName(), resolved.displayName(), resolved.matchedBy()))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    public record ResolutionView(
            @Nullable String plan, String displayName, String matchedBy) {
    }
}