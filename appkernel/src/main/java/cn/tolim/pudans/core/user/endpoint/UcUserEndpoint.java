package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class UcUserEndpoint implements CustomEndpoint {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    private final UserService userService;

    private final UserGroupService userGroupService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1Uc";
        return SpringdocRouteBuilder.route()
                .GET("/users/-", this::me, builder -> builder.operationId("GetMyUserDetail")
                        .tag(tag)
                        .description("Get the current user's profile and roles.")
                        .response(responseBuilder().implementation(UserView.class)))
                .GET("/users/-/permissions", this::permissions, builder -> builder.operationId("GetMyPermissions")
                        .tag(tag)
                        .description("Get permissions of the current user.")
                        .response(responseBuilder().implementation(UserPermissionVo.class)))
                .PUT("/users/-/password", this::changeMyPassword, builder -> builder.operationId("ChangeMyPassword")
                        .tag(tag)
                        .description("Change the password of the current user.")
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .implementation(ChangeMyPasswordRequest.class))
                        .response(responseBuilder().implementation(UserView.class)))
                .build();
    }

    private Mono<ServerResponse> me(ServerRequest request) {
        return authenticated()
                .map(Authentication::getName)
                .flatMap(userService::getUser)
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    private Mono<ServerResponse> permissions(ServerRequest request) {
        return authenticated()
                .map(Authentication::getName)
                .flatMap(username -> userGroupService
                        .getRoleNamesByUsername(username)
                        .collectList()
                        .map(roles -> new UserPermissionVo(username, roles)))
                .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    private Mono<ServerResponse> changeMyPassword(ServerRequest request) {
        return authenticated()
                .map(Authentication::getName)
                .flatMap(username -> request.bodyToMono(ChangeMyPasswordRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is empty")))
                        .flatMap(changeRequest -> userService.getUser(username).flatMap(user -> {
                            var passwordSet = StringUtils.hasText(user.getSpec().getPassword());
                            var verifyPassword = passwordSet
                                    ? verifyOldPassword(username, changeRequest.oldPassword())
                                    : Mono.just(true);
                            return verifyPassword
                                    .flatMap(ignored ->
                                            userService.updateWithRawPassword(username, changeRequest.password()))
                                    .defaultIfEmpty(user);
                        })))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    private Mono<Boolean> verifyOldPassword(String username, String oldPassword) {
        if (!StringUtils.hasText(oldPassword)) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "Old password is required.", "problemDetail.user.oldPassword.required", null));
        }
        return userService
                .confirmPassword(username, oldPassword)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new UnsatisfiedAttributeValueException(
                        "Old password is incorrect.", "problemDetail.user.oldPassword.notMatch", null)));
    }

    private Mono<Authentication> authenticated() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(trustResolver::isAuthenticated)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Anonymous user is not allowed to access user profile.")));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.UC + "/v1alpha1");
    }


    record ChangeMyPasswordRequest(String oldPassword, String password) {}


    record UserPermissionVo(String username, java.util.List<String> roles) {}
}
