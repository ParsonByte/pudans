package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.infra.properties.PasskeyProperties;
import cn.tolim.pudans.core.infra.webauthn.Passkeys;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class PasskeyRegistrationEndpoint implements CustomEndpoint {

    private static final String CHALLENGE_ATTRIBUTE = PasskeyRegistrationEndpoint.class.getName() + ".CHALLENGE";

    private final Passkeys passkeyService;

    private final PasskeyProperties properties;

    private final Accounts accounts;

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "PasskeyV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/passkeys/registration/options", this::registrationOptions, builder -> builder
                        .operationId("PasskeyRegistrationOptions")
                        .description("Create WebAuthn registration options for the current user.")
                        .tag(tag)
                        .response(responseBuilder().implementation(PasskeyOptionsVo.class)))
                .POST("/passkeys/registration/verify", this::verifyRegistration, builder -> builder
                        .operationId("PasskeyRegistrationVerify")
                        .description("Verify a WebAuthn attestation and persist the passkey credential.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(String.class))
                        .response(responseBuilder().implementation(UserConnection.class)))
                .GET("/passkeys", this::list, builder -> builder.operationId("ListPasskeys")
                        .description("List the current user's passkey devices.")
                        .tag(tag)
                        .response(responseBuilder().implementationArray(UserConnection.class)))
                .build();
    }

    private Mono<ServerResponse> registrationOptions(ServerRequest request) {
        return currentUsername()
                .flatMap(accounts::getUser)
                .flatMap(user -> passkeyService
                        .listPasskeys(user.getMetadata().getName())
                        .map(connection -> Map.of(
                                "type", "public-key",
                                "id", connection.getSpec().getProviderUserId()))
                        .collectList()
                        .flatMap(excludeCredentials -> {
                            var challenge = passkeyService.newChallenge();
                            return request.exchange()
                                    .getSession()
                                    .doOnNext(session -> session.getAttributes()
                                            .put(CHALLENGE_ATTRIBUTE, challenge))
                                    .then(ServerResponse.ok()
                                            .bodyValue(new PasskeyOptionsVo(Map.of(
                                                    "challenge", challenge,
                                                    "rp", Map.of(
                                                            "id", properties.getRpId(),
                                                            "name", properties.getRpName()),
                                                    "user", Map.of(
                                                            "id", Passkeys.base64Url(
                                                                    Passkeys.usernameHandle(
                                                                            user.getMetadata().getName())),
                                                            "name", user.getMetadata().getName(),
                                                            "displayName",
                                                            user.getSpec().getDisplayName() == null
                                                                    ? user.getMetadata().getName()
                                                                    : user.getSpec().getDisplayName()),
                                                    "pubKeyCredParams", List.of(
                                                            Map.of("type", "public-key", "alg", -7),
                                                            Map.of("type", "public-key", "alg", -257)),
                                                    "timeout", properties.getTimeout(),
                                                    "attestation", "none",
                                                    "authenticatorSelection", Map.of(
                                                            "residentKey", "preferred",
                                                            "userVerification", "preferred"),
                                                    "excludeCredentials", excludeCredentials))));
                        }));
    }

    private Mono<ServerResponse> verifyRegistration(ServerRequest request) {
        return currentUsername()
                .flatMap(username -> request.exchange()
                        .getSession()
                        .map(session -> {
                            var challenge = (String) session.getAttributes().remove(CHALLENGE_ATTRIBUTE);
                            if (challenge == null) {
                                throw new ServerWebInputException("注册挑战缺失或已过期。");
                            }
                            return challenge;
                        })
                        .flatMap(challenge -> request.bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                                .flatMap(json -> passkeyService.verifyRegistration(username, json, challenge))))
                .flatMap(connection -> ServerResponse.ok().bodyValue(connection));
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return currentUsername()
                .flatMapMany(passkeyService::listPasskeys)
                .collectList()
                .flatMap(connections -> ServerResponse.ok().bodyValue(connections));
    }

    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(trustResolver::isAuthenticated)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.FORBIDDEN, "需要身份认证。")));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    record PasskeyOptionsVo(Map<String, Object> publicKey) {}
}