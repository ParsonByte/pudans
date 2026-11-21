package cn.tolim.pudans.core.risk.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.risk.RiskGuard;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.LinkedHashMap;
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
public class RiskRuleEndpoint implements CustomEndpoint {

    private final RiskGuard riskGuard;

    private final AuditLog auditLog;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "RiskRuleV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/riskrules/bans", this::bans, builder -> builder.operationId("ListRiskBans")
                        .description("List active risk control bans with their remaining duration.")
                        .tag(tag)
                        .response(responseBuilder()
                                .implementationArray(RiskGuard.RiskBan.class)))
                .DELETE("/riskrules/unban", this::unban, builder -> builder.operationId("UnbanRiskSubject")
                        .description("Lift the active ban of a scene subject, e.g. an IP address.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("scene")
                                .required(true)
                                .description("Risk scene of the ban, e.g. request-flood.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("subject")
                                .required(true)
                                .description("Banned subject, e.g. the client IP.")
                                .implementation(String.class))
                        .response(responseBuilder().implementation(Void.class)))
                .build();
    }

    private Mono<ServerResponse> bans(ServerRequest request) {
        return riskGuard
                .listBans()
                .collectList()
                .flatMap(bans -> ServerResponse.ok().bodyValue(bans));
    }

    private Mono<ServerResponse> unban(ServerRequest request) {
        var scene = request.queryParam("scene").filter(StringUtils::hasText).orElse(null);
        var subject = request.queryParam("subject").filter(StringUtils::hasText).orElse(null);
        if (scene == null) {
            return Mono.error(new ServerWebInputException("查询参数 'scene' 为必填项。"));
        }
        if (subject == null) {
            return Mono.error(new ServerWebInputException("查询参数 'subject' 为必填项。"));
        }
        return riskGuard
                .unban(scene, subject)
                .doOnNext(deletedCount -> recordUnban(
                        request, scene, subject, deletedCount != null && deletedCount > 0))
                .then(ServerResponse.noContent().build());
    }

    private void recordUnban(ServerRequest request, String scene, String subject, boolean deleted) {
        var context = new LinkedHashMap<String, Object>();
        context.put("scene", scene);
        context.put("subject", subject);
        context.put("banKeyDeleted", deleted);
        auditLog.record(
                request.exchange(),
                "RISK_UNBAN",
                "RiskRule",
                scene + ":" + subject,
                "SUCCESS",
                0L,
                context);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
