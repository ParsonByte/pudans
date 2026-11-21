package cn.tolim.pudans.core.audit.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.core.audit.AuditLog;
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

    // 注册用户页面导航记录接口。
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

    // 记录页面导航并返回关联的审计编号。
    Mono<ServerResponse> record(ServerRequest request) {
        return request.bodyToMono(NavigationEvent.class)
                .defaultIfEmpty(new NavigationEvent(null))
                .flatMap(event -> {
                    var path = normalize(event.path());
                    if (path == null) {
                        throw new ServerWebInputException("页面路径必须是以 / 开头的相对路径。");
                    }
                    var exchange = request.exchange();
                    exchange.getAttributes().put(AuditLog.OPERATION_ATTRIBUTE, "NAVIGATION");
                    exchange.getAttributes().put(AuditLog.RESOURCE_TYPE_ATTRIBUTE, "Page");
                    exchange.getAttributes().put(AuditLog.RESOURCE_NAME_ATTRIBUTE, path);
                    exchange.getAttributes().put(AuditLog.RESULT_ATTRIBUTE, "SUCCESS");
                    var auditId = (String) exchange.getAttributes().get(AuditLog.AUDIT_ID_ATTRIBUTE);
                    var traceFlowId =
                            (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE);
                    return ServerResponse.ok().bodyValue(new NavigationResult(auditId, traceFlowId));
                });
    }

    // 校验并截断页面路径，非法路径返回空值。
    private static String normalize(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith("/") || path.startsWith("//")) {
            return null;
        }
        return path.length() > 512 ? path.substring(0, 512) : path;
    }

    // 返回导航接口所属的 API 版本。
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    public record NavigationEvent(String path) {}

    public record NavigationResult(String auditId, String traceFlowId) {}
}
