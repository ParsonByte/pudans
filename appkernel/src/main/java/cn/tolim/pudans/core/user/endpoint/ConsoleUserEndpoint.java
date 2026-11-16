package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Objects;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


@Component
class ConsoleUserEndpoint implements CustomEndpoint {

    private final UserService userService;

    ConsoleUserEndpoint(UserService userService) {
        this.userService = userService;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1Console";
        return RouterFunctions.nest(
                RequestPredicates.path("/users"),
                SpringdocRouteBuilder.route()
                        .POST("/{username}/disable", this::handleDisableUser, ops -> {
                            ops.operationId("DisableUser")
                                    .tag(tag)
                                    .description("Disable user by metadata.name")
                                    .parameter(Builder.parameterBuilder()
                                            .name("username")
                                            .in(ParameterIn.PATH)
                                            .description("User metadata.name")
                                            .required(true)
                                            .implementation(String.class))
                                    .response(responseBuilder()
                                            .implementation(UserView.class)
                                            .description("The user has been disabled."));
                        })
                        .POST("/{username}/enable", this::handleEnableUser, ops -> {
                            ops.operationId("EnableUser")
                                    .tag(tag)
                                    .description("Enable user by metadata.name")
                                    .parameter(Builder.parameterBuilder()
                                            .name("username")
                                            .in(ParameterIn.PATH)
                                            .description("User metadata.name")
                                            .required(true)
                                            .implementation(String.class))
                                    .response(responseBuilder()
                                            .implementation(UserView.class)
                                            .description("The user has been enabled."));
                        })
                        .build());
    }

    private Mono<ServerResponse> handleEnableUser(ServerRequest request) {
        return userService
                .enable(request.pathVariable("username"))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("problemDetail.user.notFoundOrEnabled")))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    private Mono<ServerResponse> handleDisableUser(ServerRequest request) {
        var username = request.pathVariable("username");
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("problemDetail.authentication.required")))
                .filter(currentUsername -> !Objects.equals(currentUsername, username))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("problemDetail.user.cannotDisableSelf")))
                .then(Mono.defer(() -> userService.disable(username)))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("problemDetail.user.notFoundOrDisabled")))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(ApiGroups.API, ApiGroups.VERSION);
    }
}