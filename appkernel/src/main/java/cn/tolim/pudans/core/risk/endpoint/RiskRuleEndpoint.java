package cn.tolim.pudans.core.risk.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.risk.service.RiskControlService;
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

    private final RiskControlService riskControlService;

    private final AuditService auditService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "RiskRuleV1alpha1";
        return SpringdocRouteBuilder.route()
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

    private Mono<ServerResponse> unban(ServerRequest request) {
        var scene = request.queryParam("scene").filter(StringUtils::hasText).orElse(null);
        var subject = request.queryParam("subject").filter(StringUtils::hasText).orElse(null);
        if (scene == null) {
            return Mono.error(new ServerWebInputException("Query param 'scene' is required."));
        }
        if (subject == null) {
            return Mono.error(new ServerWebInputException("Query param 'subject' is required."));
        }
        return riskControlService
                .unban(scene, subject)
                .doOnNext(deletedCount -> recordUnban(request, scene, subject, deletedCount != null && deletedCount > 0))
                .then(ServerResponse.noContent().build());
    }

    private void recordUnban(ServerRequest request, String scene, String subject, boolean deleted) {
        var context = new LinkedHashMap<String, Object>();
        context.put("scene", scene);
        context.put("subject", subject);
        context.put("banKeyDeleted", deleted);
        auditService.record(
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
