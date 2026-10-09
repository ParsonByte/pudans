package cn.tolim.pudans.core.user.endpoint;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ANONYMOUS_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.AUTHENTICATED_ROLE_NAME;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.properties.PasskeyProperties;
import cn.tolim.pudans.infra.webauthn.PasskeyService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class PasskeyAssertionEndpoint implements CustomEndpoint {

    private static final String CHALLENGE_ATTRIBUTE = PasskeyAssertionEndpoint.class.getName() + ".CHALLENGE";
    private static final String USERNAME_ATTRIBUTE = PasskeyAssertionEndpoint.class.getName() + ".USERNAME";

    private final PasskeyService passkeyService;

    private final PasskeyProperties properties;

    private final UserGroupService userGroupService;

    private final ServerSecurityContextRepository securityContextRepository;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "PasskeyV1alpha1Public";
        return SpringdocRouteBuilder.route()
                .POST("/passkeys/assertion/options", this::assertionOptions, builder -> builder
                        .operationId("PasskeyAssertionOptions")
                        .description("Create WebAuthn assertion options.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                                .required(false)
                                .implementation(PasskeyAssertionOptionsRequest.class))
                        .response(responseBuilder().implementation(PasskeyAssertionOptionsVo.class)))
                .POST("/passkeys/assertion/verify", this::verifyAssertion, builder -> builder
                        .operationId("PasskeyAssertionVerify")
                        .description("Verify a WebAuthn assertion and sign the user in.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(String.class))
                        .response(responseBuilder().implementation(PasskeyAssertionResult.class)))
                .build();
    }

    private Mono<ServerResponse> assertionOptions(ServerRequest request) {
        return request.bodyToMono(PasskeyAssertionOptionsRequest.class)
                .defaultIfEmpty(new PasskeyAssertionOptionsRequest(null))
                .flatMap(body -> {
                    var challenge = passkeyService.newChallenge();
                    var allowCredentials = StringUtils.hasText(body.username())
                            ? passkeyService
                            .listPasskeys(body.username())
                            .map(connection -> Map.of(
                                    "type", "public-key",
                                    "id", connection.getSpec().getProviderUserId()))
                            .collectList()
                            : Mono.just(List.of());
                    return allowCredentials.flatMap(credentials -> request.exchange()
                            .getSession()
                            .doOnNext(session -> {
                                session.getAttributes().put(CHALLENGE_ATTRIBUTE, challenge);
                                session.getAttributes().put(USERNAME_ATTRIBUTE, body.username());
                            })
                            .then(ServerResponse.ok()
                                    .bodyValue(new PasskeyAssertionOptionsVo(Map.of(
                                            "challenge", challenge,
                                            "timeout", properties.getTimeout(),
                                            "rpId", properties.getRpId(),
                                            "allowCredentials", credentials,
                                            "userVerification", "preferred")))));
                });
    }

    private Mono<ServerResponse> verifyAssertion(ServerRequest request) {
        return request.exchange()
                .getSession()
                .map(session -> {
                    var challenge = (String) session.getAttributes().remove(CHALLENGE_ATTRIBUTE);
                    if (challenge == null) {
                        throw new ServerWebInputException("Assertion challenge is missing or expired.");
                    }
                    return challenge;
                })
                .flatMap(challenge -> request.bodyToMono(String.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is required.")))
                        .flatMap(json -> passkeyService.verifyAssertion(json, challenge)))
                .flatMap(result -> establish(request, result.username()))
                .flatMap(username -> ServerResponse.ok()
                        .bodyValue(new PasskeyAssertionResult(true, username)));
    }

    private Mono<String> establish(ServerRequest request, String username) {
        return userGroupService
                .getRoleNamesByUsername(username)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .concatWithValues(
                        new SimpleGrantedAuthority(ROLE_PREFIX + AUTHENTICATED_ROLE_NAME),
                        new SimpleGrantedAuthority(ROLE_PREFIX + ANONYMOUS_ROLE_NAME))
                .distinct()
                .collectList()
                .flatMap(authorities -> {
                    var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    return securityContextRepository
                            .save(request.exchange(), new SecurityContextImpl(authentication))
                            .doOnSuccess(ignored -> {
                                request.exchange()
                                        .getAttributes()
                                        .put(AuditService.USERNAME_ATTRIBUTE, username);
                                request.exchange()
                                        .getAttributes()
                                        .put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
                                request.exchange()
                                        .getAttributes()
                                        .put(AuditService.RESULT_ATTRIBUTE, "SUCCESS");
                                request.exchange()
                                        .getAttributes()
                                        .put(AuditService.CONTEXT_ATTRIBUTE, "passkey");
                            })
                            .thenReturn(username);
                });
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.AUTH + "/v1alpha1");
    }


    record PasskeyAssertionOptionsRequest(String username) {}


    record PasskeyAssertionOptionsVo(Map<String, Object> publicKey) {}


    record PasskeyAssertionResult(boolean authenticated, String username) {}
}