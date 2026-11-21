package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
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

    private final Accounts accounts;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    // 注入账号服务与内置账户策略以处理用户状态
    ConsoleUserEndpoint(Accounts accounts, BuiltinEntityPolicy builtinEntityPolicy) {
        this.accounts = accounts;
        this.builtinEntityPolicy = builtinEntityPolicy;
    }

    // 注册禁用与启用用户的管理路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1";
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

    // 启用用户，超管账户变更需要超级管理员权限
    private Mono<ServerResponse> handleEnableUser(ServerRequest request) {
        var username = request.pathVariable("username");
        return builtinEntityPolicy
                .checkSuperAdminAccountChange(username)
                .then(Mono.defer(() -> accounts.enable(username)))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("用户不存在或已启用。")))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    // 禁止禁用自己后再执行禁用
    private Mono<ServerResponse> handleDisableUser(ServerRequest request) {
        var username = request.pathVariable("username");
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("请先登录。")))
                .filter(currentUsername -> !Objects.equals(currentUsername, username))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("不能禁用当前登录的用户。")))
                .then(Mono.defer(() -> builtinEntityPolicy.checkSuperAdminAccountChange(username)))
                .then(Mono.defer(() -> accounts.disable(username)))
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("用户不存在或已禁用。")))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user)));
    }

    // 返回用户接口组版本
    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(ApiGroups.API, ApiGroups.VERSION);
    }
}