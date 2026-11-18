package cn.tolim.pudans.core.rbac.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.rbac.service.PermissionCatalogService;

@Component
@RequiredArgsConstructor
public class PermissionCatalogEndpoint implements CustomEndpoint {

    private final PermissionCatalogService permissionCatalogService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "PermissionCatalogV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/permission-catalog", this::catalog, builder -> builder.operationId("getPermissionCatalog")
                        .description("Get the console permission catalog defined by role templates.")
                        .tag(tag)
                        .response(responseBuilder()
                                .implementation(PermissionCatalogService.PermissionCatalog.class)))
                .build();
    }

    private Mono<ServerResponse> catalog(ServerRequest request) {
        return permissionCatalogService
                .catalog()
                .flatMap(catalog -> ServerResponse.ok().bodyValue(catalog));
    }
}
