package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.infra.properties.PasskeyProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import cn.tolim.pudans.infra.webauthn.Passkeys;
import cn.tolim.pudans.security.authentication.ExternalLogins;
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

    private final Passkeys passkeyService;

    private final ParameterGroups parameterGroups;

    private final PasskeyProperties properties;

    private final ExternalLogins externalLogins;

    // 注册免密登录的断言选项与校验路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "PasskeyV1alpha1";
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

    // 登录开关关闭时拒绝，否则生成断言选项
    private Mono<ServerResponse> assertionOptions(ServerRequest request) {
        return passkeyLoginEnabled()
                .flatMap(enabled -> Boolean.FALSE.equals(enabled)
                        ? Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Passkey 登录已禁用。"))
                        : doAssertionOptions(request));
    }

    // 默认允许免密登录，除非显式关闭
    private Mono<Boolean> passkeyLoginEnabled() {
        return parameterGroups
                .getSession()
                .map(session -> !Boolean.FALSE.equals(session.getPasskeyLoginEnabled()))
                .defaultIfEmpty(true);
    }

    // 生成挑战并保存会话后返回断言选项
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
                            .doOnNext(session -> session.getAttributes().put(CHALLENGE_ATTRIBUTE, challenge))
                            .then(ServerResponse.ok()
                                    .bodyValue(new PasskeyAssertionOptionsVo(Map.of(
                                            "challenge", challenge,
                                            "timeout", properties.getTimeout(),
                                            "rpId", properties.getRpId(),
                                            "allowCredentials", credentials,
                                            "userVerification", "preferred")))));
                });
    }

    // 登录开关关闭时拒绝，否则校验断言
    private Mono<ServerResponse> verifyAssertion(ServerRequest request) {
        return passkeyLoginEnabled()
                .flatMap(enabled -> Boolean.FALSE.equals(enabled)
                        ? Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Passkey 登录已禁用。"))
                        : doVerifyAssertion(request));
    }

    // 消费会话挑战并完成登录流程
    private Mono<ServerResponse> doVerifyAssertion(ServerRequest request) {
        return request.exchange()
                .getSession()
                .map(session -> {
                    var challenge = (String) session.getAttributes().remove(CHALLENGE_ATTRIBUTE);
                    if (challenge == null) {
                        throw new ServerWebInputException("断言挑战缺失或已过期。");
                    }
                    return challenge;
                })
                .flatMap(challenge -> request.bodyToMono(String.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(json -> passkeyService.verifyAssertion(json, challenge)))
                .flatMap(result -> externalLogins.complete(
                        request.exchange(), result.username(), "passkey"))
                .flatMap(completion -> ServerResponse.ok()
                        .bodyValue(completion.twoFactorRequired()
                                ? new PasskeyAssertionResult(false, completion.username(), true)
                                : new PasskeyAssertionResult(true, completion.username(), false)))
                .onErrorResume(
                        LoginRejectedException.class,
                        error -> ServerResponse.status(HttpStatus.FORBIDDEN)
                                .bodyValue(Map.of("error", error.getReason())));
    }

    // 返回当前免密登录接口版本
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    record PasskeyAssertionOptionsRequest(String username) {}


    record PasskeyAssertionOptionsVo(Map<String, Object> publicKey) {}


    record PasskeyAssertionResult(boolean authenticated, String username, boolean twoFactorRequired) {}
}