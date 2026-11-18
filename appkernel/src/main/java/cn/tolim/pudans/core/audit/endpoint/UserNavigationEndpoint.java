package cn.tolim.pudans.core.audit.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.audit.service.AuditService;
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
public class UserNavigationEndpoint implements CustomEndpoint {

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserNavigationV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/usernavigations", this::record, builder -> builder.operationId("RecordUserNavigation")
                        .description(
                                "Record a client-side page navigation. The request itself is audited as a Page "
                                        + "navigation record and returns the audit id used to chain following "
                                        + "requests.")
                        .tag(tag)
                        .response(responseBuilder().implementation(NavigationResult.class)))
                .build();
    }

    Mono<ServerResponse> record(ServerRequest request) {
        return request.bodyToMono(NavigationEvent.class)
                .defaultIfEmpty(new NavigationEvent(null))
                .flatMap(event -> {
                    var path = normalize(event.path());
                    if (path == null) {
                        throw new ServerWebInputException("页面路径必须是以 / 开头的相对路径。");
                    }
                    var exchange = request.exchange();
                    exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "NAVIGATION");
                    exchange.getAttributes().put(AuditService.RESOURCE_TYPE_ATTRIBUTE, "Page");
                    exchange.getAttributes().put(AuditService.RESOURCE_NAME_ATTRIBUTE, path);
                    exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "SUCCESS");
                    var auditId = (String) exchange.getAttributes().get(AuditService.AUDIT_ID_ATTRIBUTE);
                    var traceFlowId =
                            (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE);
                    return ServerResponse.ok().bodyValue(new NavigationResult(auditId, traceFlowId));
                });
    }

    private static String normalize(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith("/") || path.startsWith("//")) {
            return null;
        }
        return path.length() > 512 ? path.substring(0, 512) : path;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    public record NavigationEvent(String path) {}

    public record NavigationResult(String auditId, String traceFlowId) {}
}
