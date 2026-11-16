package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.infra.properties.PasskeyProperties;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import cn.tolim.pudans.infra.webauthn.PasskeyService;
import cn.tolim.pudans.security.authentication.ExternalLoginCompletionService;
import cn.tolim.pudans.security.authentication.LoginRejectedException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
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

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final PasskeyProperties properties;

    private final ExternalLoginCompletionService externalLoginCompletionService;

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
        return passkeyLoginEnabled()
                .flatMap(enabled -> Boolean.FALSE.equals(enabled)
                        ? Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Passkey login is disabled."))
                        : doAssertionOptions(request));
    }

    private Mono<Boolean> passkeyLoginEnabled() {
        return parameterSettingGroupService
                .getSession()
                .map(session -> !Boolean.FALSE.equals(session.getPasskeyLoginEnabled()))
                .defaultIfEmpty(true);
    }

    private Mono<ServerResponse> doAssertionOptions(ServerRequest request) {
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
        return passkeyLoginEnabled()
                .flatMap(enabled -> Boolean.FALSE.equals(enabled)
                        ? Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Passkey login is disabled."))
                        : doVerifyAssertion(request));
    }

    private Mono<ServerResponse> doVerifyAssertion(ServerRequest request) {
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
                .flatMap(result -> externalLoginCompletionService.complete(
                        request.exchange(), result.username(), "passkey"))
                .flatMap(completion -> ServerResponse.ok()
                        .bodyValue(completion.twoFactorRequired()
                                ? new PasskeyAssertionResult(
                                        false,
                                        completion.username(),
                                        true,
                                        "/challenges/two-factor/totp")
                                : new PasskeyAssertionResult(
                                        true, completion.username(), false, null)))
                .onErrorResume(
                        LoginRejectedException.class,
                        error -> ServerResponse.status(HttpStatus.FORBIDDEN)
                                .bodyValue(Map.of("error", error.getReason())));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    record PasskeyAssertionOptionsRequest(String username) {}


    record PasskeyAssertionOptionsVo(Map<String, Object> publicKey) {}


    record PasskeyAssertionResult(
            boolean authenticated, String username, boolean twoFactorRequired, String redirectUrl) {}
}