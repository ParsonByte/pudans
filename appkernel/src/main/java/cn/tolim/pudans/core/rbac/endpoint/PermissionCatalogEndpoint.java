package cn.tolim.pudans.core.rbac.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.rbac.permission.PermissionDirectory;

@Component
@RequiredArgsConstructor
public class PermissionCatalogEndpoint implements CustomEndpoint {

    private final PermissionDirectory permissionDirectory;

    // 注册权限目录查询路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "PermissionCatalogV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/permission-catalog", this::catalog, builder -> builder.operationId("getPermissionCatalog")
                        .description("Get the console permission catalog defined by role templates.")
                        .tag(tag)
                        .response(responseBuilder()
                                .implementation(PermissionDirectory.PermissionCatalog.class)))
                .build();
    }

    // 返回由角色模板构建的权限目录。
    private Mono<ServerResponse> catalog(ServerRequest request) {
        return permissionDirectory
                .catalog()
                .flatMap(catalog -> ServerResponse.ok().bodyValue(catalog));
    }
}
