package cn.tolim.pudans.core.risk.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.risk.RiskAnalytics;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.contract.risk.dto.RiskOverview;
import cn.tolim.pudans.infra.utils.RangeDays;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskRuleEndpoint implements CustomEndpoint {

    private final RiskGuard riskGuard;

    private final AuditLog auditLog;

    private final RiskAnalytics riskAnalytics;

    // 注册风险封禁查询与解封路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "RiskRuleV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/riskrules/overview", this::overview, builder -> builder.operationId("GetRiskOverview")
                        .description("Aggregate real risk control events, rules, bans, heatmap and relations.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("days")
                                .required(false)
                                .description("Analytics range in days, between 1 and 90. Defaults to 7.")
                                .implementation(Integer.class))
                        .response(responseBuilder().implementation(RiskOverview.class)))
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

    // 聚合指定时间范围内的风险态势数据。
    private Mono<ServerResponse> overview(ServerRequest request) {
        return riskAnalytics
                .overview(RangeDays.resolve(request))
                .flatMap(overview -> ServerResponse.ok().bodyValue(overview))
                .onErrorResume(error -> {
                    log.warn("无法聚合风险概览", error);
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "风险概览聚合失败。"));
                });
    }

    // 列出当前生效的风险封禁记录。
    private Mono<ServerResponse> bans(ServerRequest request) {
        return riskGuard
                .listBans()
                .collectList()
                .flatMap(bans -> ServerResponse.ok().bodyValue(bans));
    }

    // 校验参数后解除指定主体在场景下的封禁。
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

    // 把解封操作写入审计，记录删除结果。
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

    // 返回风险接口所属的 API 版本。
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
