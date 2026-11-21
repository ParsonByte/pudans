package cn.tolim.pudans.core.user.endpoint;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.ExtensionUtil.notDeleting;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.index.query.Queries;
import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.oidc.OidcClient;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
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
class UserConnectionEndpoint implements CustomEndpoint {

    private static final String PASSKEY_PROVIDER = "passkey";

    private final ReactiveExtensionClient client;

    private final Accounts accounts;

    private final Connections connectionService;

    private final OidcClient oidcClient;

    private final SecurityParameters securitySettings;

    private final AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    private static String stateKey(String registrationId) {
        return UserConnectionEndpoint.class.getName() + ".STATE." + registrationId;
    }

    private static String connectionName(String registrationId, String subject) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest((registrationId + '\0' + subject).getBytes(StandardCharsets.UTF_8));
            return "oidc-" + HexFormat.of().formatHex(digest, 0, 28);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

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

    private Mono<ServerResponse> list(ServerRequest request) {
        return currentUsername()
                .flatMapMany(connectionService::listConnections)
                .collectList()
                .flatMap(connections -> ServerResponse.ok().bodyValue(connections));
    }

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

    private Mono<UserConnection> createConnection(
            String username, String registrationId, OidcClient.OidcUserInfo userInfo) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(Queries.equal("spec.registrationId", registrationId))
                .andQuery(Queries.equal("spec.providerUserId", userInfo.subject()))
                .build();
        return client.listAll(UserConnection.class, options, defaultSort())
                .next()
                .flatMap(existing -> Mono.<UserConnection>error(new ServerWebInputException(
                        "此 OIDC 身份已绑定。")))
                .switchIfEmpty(Mono.defer(() -> {
                    var connection = new UserConnection();
                    var metadata = new Metadata();
                    metadata.setName(connectionName(registrationId, userInfo.subject()));
                    connection.setMetadata(metadata);
                    var spec = new UserConnection.UserConnectionSpec();
                    spec.setRegistrationId(registrationId);
                    spec.setUsername(username);
                    spec.setProviderUserId(userInfo.subject());
                    spec.setDisplayName(userInfo.name());
                    spec.setUpdatedAt(java.time.Instant.now());
                    connection.setSpec(spec);
                    return client.create(connection);
                }));
    }

    private Mono<ServerResponse> unbind(ServerRequest request) {
        var name = request.pathVariable("name");
        return currentUsername()
                .flatMap(username -> client.fetch(UserConnection.class, name).flatMap(connection -> {
                    if (!username.equals(connection.getSpec().getUsername())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "不能删除其他用户的连接。"));
                    }
                    return canRemove(username, connection).flatMap(canRemove -> canRemove
                            ? client.delete(connection)
                            : Mono.error(new ServerWebInputException(
                                    "没有 Passkey 时不能移除唯一的登录方式。")));
                }))
                .flatMap(deleted -> ServerResponse.ok().bodyValue(deleted));
    }

    private Mono<Boolean> canRemove(String username, UserConnection target) {
        return connectionService
                .listConnections(username)
                .filter(connection -> !connection.getMetadata().getName().equals(target.getMetadata().getName()))
                .collectList()
                .flatMap(remaining -> accounts
                        .getUser(username)
                        .map(user -> !remaining.isEmpty()
                                || StringUtils.hasText(user.getSpec().getPassword())))
                .defaultIfEmpty(false);
    }

    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(authenticationTrustResolver::isAuthenticated)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "需要身份认证。")));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    record OidcProviderVo(String registrationId, String displayName, String issuerUri) {}


    record OidcAuthorizeUrlVo(String authorizationUrl, String state) {}


    record OidcBindRequest(String registrationId, String code, String redirectUri, String state) {}
}