package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.ai.dto.AiQuotaSnapshot;
import cn.tolim.pudans.core.ai.quota.AiQuotaGuard;
import cn.tolim.pudans.core.ai.quota.AiQuotas;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiQuotaEndpoint implements CustomEndpoint {

    private static final String TAG = "AiQuotaV1alpha1";

    private final AiQuotas quotas;

    private final AiQuotaGuard guard;

    private final ReactiveRequestIdentity identity;

    // 注册当前用户配额快照与管理员指定主体快照。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/quotas/-/snapshot", this::snapshot, builder -> builder.operationId("getAiQuotaSnapshot")
                        .description("Get the effective quota snapshot for the current subject.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiQuotaSnapshot.class)))
                .build();
    }

    private Mono<ServerResponse> snapshot(ServerRequest request) {
        var subjectType = request.queryParam("subjectType").orElse(null);
        var subjectId = request.queryParam("subjectId").orElse(null);
        if (subjectType != null || subjectId != null) {
            if (!StringUtils.hasText(subjectType) || !StringUtils.hasText(subjectId)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-quota-subject-required",
                        "查询指定主体时必须同时提供 subjectType 和 subjectId。"));
            }
            return identity
                    .resolve(request)
                    .zipWith(identity.roles())
                    .flatMap(tuple -> {
                        guard.requireInspection(tuple.getT2());
                        return quotas.snapshot(subjectType, subjectId);
                    })
                    .flatMap(snapshot -> ServerResponse.ok().bodyValue(snapshot));
        }
        return identity
                .resolve(request)
                .zipWith(identity.roles())
                .flatMap(tuple -> quotas.snapshot(tuple.getT1(), tuple.getT2()))
                .flatMap(snapshot -> ServerResponse.ok().bodyValue(snapshot));
    }
}
