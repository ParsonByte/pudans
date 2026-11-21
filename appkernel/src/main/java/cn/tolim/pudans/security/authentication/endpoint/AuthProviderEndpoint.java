package cn.tolim.pudans.security.authentication.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.security.contract.AuthProvider;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.security.authentication.AuthProviders;
import cn.tolim.pudans.security.authentication.ListedAuthProvider;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class AuthProviderEndpoint implements CustomEndpoint {

    private final AuthProviders authProviders;

    // 复用统一分组版本，保证接口路径前缀一致
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    // 注册认证提供方的列表、启用与禁用路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "AuthProviderV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET(
                        "/auth-providers",
                        this::listAuthProviders,
                        builder -> builder.operationId("listAuthProviders")
                                .description("Lists all auth providers")
                                .tag(tag)
                                .response(responseBuilder().implementationArray(ListedAuthProvider.class)))
                .PUT(
                        "/auth-providers/{name}/enable",
                        this::enableAuthProvider,
                        builder -> builder.operationId("enableAuthProvider")
                                .description("Enables an auth provider")
                                .tag(tag)
                                .parameter(parameterBuilder()
                                        .name("name")
                                        .description("metadata.name of the auth provider to enable.")
                                        .in(ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(AuthProvider.class)))
                .PUT(
                        "/auth-providers/{name}/disable",
                        this::disableAuthProvider,
                        builder -> builder.operationId("disableAuthProvider")
                                .description("Disables an auth provider")
                                .tag(tag)
                                .parameter(parameterBuilder()
                                        .name("name")
                                        .description("metadata.name of the auth provider to disable.")
                                        .in(ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(AuthProvider.class)))
                .build();
    }

    // 启用指定提供方并返回更新后的状态
    private Mono<ServerResponse> enableAuthProvider(ServerRequest request) {
        String name = request.pathVariable("name");
        return authProviders
                .enable(name)
                .flatMap(authProvider -> ServerResponse.ok().bodyValue(authProvider));
    }

    // 停用指定提供方并返回更新后的状态
    private Mono<ServerResponse> disableAuthProvider(ServerRequest request) {
        String name = request.pathVariable("name");
        return authProviders
                .disable(name)
                .flatMap(authProvider -> ServerResponse.ok().bodyValue(authProvider));
    }

    // 列出全部提供方，供前端登录页选择展示
    Mono<ServerResponse> listAuthProviders(ServerRequest request) {
        return authProviders
                .listAll()
                .flatMap(providers -> ServerResponse.ok().bodyValue(providers));
    }
}