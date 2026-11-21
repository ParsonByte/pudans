package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.oidc.OidcClient;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class UserConnectionEndpoint implements CustomEndpoint {

    private final Connections connectionService;

    private final OidcClient oidcClient;

    private final SecurityParameters securitySettings;

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    // 为每个提供方生成会话内状态键
    private static String stateKey(String registrationId) {
        return UserConnectionEndpoint.class.getName() + ".STATE." + registrationId;
    }

    // 注册身份连接查询、绑定与解绑路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserConnectionV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/userconnections", this::list, builder -> builder.operationId("ListMyConnections")
                        .description("List the current user's identity bindings (Feishu, OIDC and Passkeys).")
                        .tag(tag)
                        .response(responseBuilder().implementationArray(UserConnection.class)))
                .GET("/userconnections/oidc/providers", this::listOidcProviders, builder -> builder
                        .operationId("ListOidcProviders")
                        .description("List the configured OIDC identity providers.")
                        .tag(tag)
                        .response(responseBuilder().implementationArray(OidcProviderVo.class)))
                .GET("/userconnections/oidc/{registrationId}/authorize-url", this::authorizeUrl, builder -> builder
                        .operationId("GetOidcAuthorizeUrl")
                        .description("Build the OIDC authorization URL used to bind an external identity.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("registrationId").required(true))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("redirectUri")
                                .required(true)
                                .implementation(String.class))
                        .response(responseBuilder().implementation(OidcAuthorizeUrlVo.class)))
                .POST("/userconnections/oidc/bind", this::bindOidc, builder -> builder.operationId("BindOidcIdentity")
                        .description("Bind an OIDC identity to the current user using the authorization code.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(OidcBindRequest.class))
                        .response(responseBuilder().implementation(UserConnection.class)))
                .DELETE("/userconnections/{name}", this::unbind, builder -> builder.operationId("UnbindConnection")
                        .description("Remove an identity binding. Rejected if it is the only sign-in method.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(UserConnection.class)))
                .build();
    }

    // 列出当前用户的全部身份连接
    private Mono<ServerResponse> list(ServerRequest request) {
        return currentUsername()
                .flatMapMany(connectionService::listConnections)
                .collectList()
                .flatMap(connections -> ServerResponse.ok().bodyValue(connections));
    }

    // 返回已配置的外部身份提供方
    private Mono<ServerResponse> listOidcProviders(ServerRequest request) {
        return currentUsername()
                .map(username -> securitySettings.oidcProviders().stream()
                        .map(provider -> new OidcProviderVo(
                                provider.getRegistrationId(),
                                provider.getDisplayName(),
                                provider.getIssuerUri()))
                        .toList())
                .flatMap(providers -> ServerResponse.ok().bodyValue(providers));
    }

    // 生成并缓存授权地址与状态
    private Mono<ServerResponse> authorizeUrl(ServerRequest request) {
        var registrationId = request.pathVariable("registrationId");
        var redirectUri = request.queryParam("redirectUri").orElseThrow(
                () -> new ServerWebInputException("查询参数 'redirectUri' 为必填项"));
        var state = UUID.randomUUID().toString();
        return currentUsername()
                .flatMap(username -> request.exchange()
                        .getSession()
                        .doOnNext(session -> session.getAttributes()
                                .put(stateKey(registrationId), state))
                        .thenReturn(new OidcAuthorizeUrlVo(
                                oidcClient.authorizationUrl(registrationId, redirectUri, state), state)))
                .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    // 校验状态后用授权码绑定外部身份
    private Mono<ServerResponse> bindOidc(ServerRequest request) {
        return currentUsername().flatMap(username -> request.bodyToMono(OidcBindRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(body -> request.exchange()
                                .getSession()
                                .flatMap(session -> {
                                    var expectedState =
                                            (String) session.getAttributes().get(stateKey(body.registrationId()));
                                    session.getAttributes().remove(stateKey(body.registrationId()));
                                    if (body.state() == null
                                            || expectedState == null
                                            || !expectedState.equals(body.state())) {
                                        return Mono.error(new ServerWebInputException("OIDC 状态无效。"));
                                    }
                                    return oidcClient
                                            .exchange(body.registrationId(), body.code(), body.redirectUri());
                                })
                                .flatMap(userInfo -> createConnection(username, body.registrationId(), userInfo))))
                .flatMap(connection -> ServerResponse.ok().bodyValue(connection));
    }

    // 用外部用户信息构造身份连接
    private Mono<UserConnection> createConnection(
            String username, String registrationId, OidcClient.OidcUserInfo userInfo) {
        var connection = new UserConnection();
        var spec = new UserConnection.UserConnectionSpec();
        spec.setRegistrationId(registrationId);
        spec.setUsername(username);
        spec.setProviderUserId(userInfo.subject());
        spec.setDisplayName(userInfo.name());
        connection.setSpec(spec);
        return connectionService.createConnection(connection);
    }

    // 删除当前用户的指定身份连接
    private Mono<ServerResponse> unbind(ServerRequest request) {
        return currentUsername()
                .flatMap(username -> connectionService.removeConnection(
                        username, request.pathVariable("name")))
                .flatMap(deleted -> ServerResponse.ok().bodyValue(deleted));
    }

    // 要求浏览器会话认证，令牌不能增删持久登录方式
    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(authenticationTrustResolver::isAuthenticated)
                .filter(authentication -> !(authentication instanceof JwtAuthenticationToken))
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "需要浏览器会话认证。")));
    }

    // 返回当前连接接口版本
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    record OidcProviderVo(String registrationId, String displayName, String issuerUri) {}


    record OidcAuthorizeUrlVo(String authorizationUrl, String state) {}


    record OidcBindRequest(String registrationId, String code, String redirectUri, String state) {}
}
