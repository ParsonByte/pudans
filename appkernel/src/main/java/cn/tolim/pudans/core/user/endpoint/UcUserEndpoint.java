package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import jakarta.validation.constraints.Size;
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

    private final Accounts accounts;

    private final Groups groups;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/users/-/self", this::me, builder -> builder.operationId("GetMySelfUserDetail")
                        .tag(tag)
                        .description("Get the current user's profile and roles.")
                        .response(responseBuilder().implementation(UserView.class)))
                .GET("/users/-/self/permissions", this::permissions, builder -> builder.operationId("GetMySelfPermissions")
                        .tag(tag)
                        .description("Get permissions of the current user.")
                        .response(responseBuilder().implementation(UserPermissionVo.class)))
                .PUT(
                        "/users/-/self/password",
                        this::changeMyPassword,
                        builder -> builder.operationId("ChangeMySelfPassword")
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
                .flatMap(accounts::getUser)
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    private Mono<ServerResponse> permissions(ServerRequest request) {
        return authenticated()
                .map(Authentication::getName)
                .flatMap(username -> groups
                        .getRoleNamesByUsername(username)
                        .collectList()
                        .map(roles -> new UserPermissionVo(username, roles)))
                .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    private Mono<ServerResponse> changeMyPassword(ServerRequest request) {
        return authenticated()
                .map(Authentication::getName)
                .flatMap(username -> request.bodyToMono(ChangeMyPasswordRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为空")))
                        .flatMap(changeRequest -> accounts.getUser(username).flatMap(user -> {
                            var passwordSet = StringUtils.hasText(user.getSpec().getPassword());
                            var verifyPassword = passwordSet
                                    ? verifyOldPassword(username, changeRequest.oldPassword())
                                    : Mono.just(true);
                            return verifyPassword.flatMap(ignored ->
                                    accounts.updateWithRawPassword(username, changeRequest.password()));
                        })))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    private Mono<Boolean> verifyOldPassword(String username, String oldPassword) {
        if (!StringUtils.hasText(oldPassword)) {
            return Mono.error(new UnsatisfiedAttributeValueException(
                    "旧密码为必填项。"));
        }
        return accounts
                .confirmPassword(username, oldPassword)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new UnsatisfiedAttributeValueException(
                        "旧密码不匹配。")));
    }

    private Mono<Authentication> authenticated() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(trustResolver::isAuthenticated)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "匿名用户不能访问用户资料。")));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    record ChangeMyPasswordRequest(String oldPassword, @Size(min = 8, max = 257) String password) {}


    record UserPermissionVo(String username, java.util.List<String> roles) {}
}
