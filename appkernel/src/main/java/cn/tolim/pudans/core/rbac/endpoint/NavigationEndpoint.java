package cn.tolim.pudans.core.rbac.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.rbac.service.PermissionCatalogService;
import cn.tolim.pudans.core.rbac.service.UiPermissionService;

@Component
@RequiredArgsConstructor
public class NavigationEndpoint implements CustomEndpoint {

    private final UiPermissionService uiPermissionService;

    private final PermissionCatalogService permissionCatalogService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "NavigationV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/navigation", this::navigation, builder -> builder.operationId("getNavigation")
                        .description("Get the workspaces and menus available to the current user.")
                        .tag(tag)
                        .response(responseBuilder()
                                .implementation(PermissionCatalogService.Navigation.class)))
                .build();
    }

    private Mono<ServerResponse> navigation(ServerRequest request) {
        return request
                .principal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.error(new ServerWebInputException("未认证")))
                .flatMap(uiPermissionService::uiPermissionsOf)
                .flatMap(permissionCatalogService::navigation)
                .flatMap(navigation -> ServerResponse.ok().bodyValue(navigation));
    }
}
