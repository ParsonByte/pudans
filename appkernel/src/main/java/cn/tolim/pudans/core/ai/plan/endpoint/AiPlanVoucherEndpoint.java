package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.plan.PlanVoucherKind;
import cn.tolim.pudans.core.ai.plan.PlanVouchers;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class AiPlanVoucherEndpoint implements CustomEndpoint {

    private static final String TAG = "AiPlanV1alpha1";

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private static final long MAX_CURSOR = Long.MAX_VALUE;

    private final PlanVouchers planVouchers;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiplan/-/vouchers", this::balance, builder -> builder
                        .operationId("getAiPlanVouchers")
                        .description("Get the current user's reset voucher balance.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(PlanVouchers.VoucherBalance.class)))
                .POST("/aiplan/-/vouchers/use", this::use, builder -> builder
                        .operationId("useAiPlanVoucher")
                        .description("Consume a reset voucher and reset the target window.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(UseRequest.class))
                        .response(responseBuilder().implementation(PlanVouchers.VoucherUseResult.class)))
                .POST("/aiplan/-/vouchers/grant", this::grant, builder -> builder
                        .operationId("grantAiPlanVouchers")
                        .description("Grant reset vouchers to users or user groups.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(GrantRequest.class))
                        .response(responseBuilder().implementation(PlanVouchers.GrantResult.class)))
                .GET("/aiplan/-/vouchers/-/history", this::history, builder -> builder
                        .operationId("listAiPlanVoucherHistory")
                        .description("List the current user's reset voucher usage history.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false)
                                .description("Page size (1-100, default 20)."))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false)
                                .description("Return records whose id is lower than this cursor."))
                        .response(responseBuilder()
                                .implementationArray(PlanVouchers.VoucherUseRecord.class)))
                .build();
    }

    private Mono<ServerResponse> balance(ServerRequest request) {
        return requests.userId(request)
                .flatMap(planVouchers::balance)
                .flatMap(balance -> ServerResponse.ok().bodyValue(balance));
    }

    private Mono<ServerResponse> use(ServerRequest request) {
        return request.bodyToMono(UseRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> {
                    if (!StringUtils.hasText(body.requestId()) || body.kind() == null) {
                        return Mono.error(new ServerWebInputException("requestId 与 kind 不能为空。"));
                    }
                    return requests.userId(request).flatMap(userId ->
                            planVouchers.use(body.requestId(), userId, body.kind()));
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> grant(ServerRequest request) {
        return request.bodyToMono(GrantRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> planVouchers.grant(
                        body.kind(), body.count(), body.userIds(), body.groupNames()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> history(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        var beforeId = AiEndpointRequests.longParam(request, "beforeId", MAX_CURSOR, 1, MAX_CURSOR);
        return requests.userId(request)
                .flatMap(userId -> planVouchers.history(userId, beforeId, limit).collectList())
                .flatMap(rows -> ServerResponse.ok().bodyValue(rows));
    }

    public record UseRequest(String requestId, PlanVoucherKind kind) {
    }

    public record GrantRequest(
            PlanVoucherKind kind, int count, List<String> userIds, List<String> groupNames) {
    }
}