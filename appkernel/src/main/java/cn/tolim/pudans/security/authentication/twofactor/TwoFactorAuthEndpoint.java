package cn.tolim.pudans.security.authentication.twofactor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.ExternalUrlSupplier;
import cn.tolim.pudans.infra.api.ValidationUtils;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.exception.RequestBodyValidationException;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import cn.tolim.pudans.security.authentication.twofactor.totp.TotpSecrets;
import cn.tolim.pudans.security.authentication.twofactor.totp.TotpConsumptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class TwoFactorAuthEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    private final Accounts accounts;

    private final TotpSecrets totpSecrets;

    private final TotpConsumptions totpConsumptions;

    private final Validator validator;

    private final PasswordEncoder passwordEncoder;

    private final ExternalUrlSupplier externalUrl;

    private final AuditLog auditLog;

    private final DomainEventPublisher domainEventPublisher;

    private final SecurityParameters securitySettings;

    public TwoFactorAuthEndpoint(
            ReactiveExtensionClient client,
            Accounts accounts,
            TotpSecrets totpSecrets,
            TotpConsumptions totpConsumptions,
            @Qualifier("webFluxValidator") Validator validator,
            PasswordEncoder passwordEncoder,
            ExternalUrlSupplier externalUrl,
            AuditLog auditLog,
            DomainEventPublisher domainEventPublisher,
            SecurityParameters securitySettings) {
        this.client = client;
        this.accounts = accounts;
        this.totpSecrets = totpSecrets;
        this.totpConsumptions = totpConsumptions;
        this.validator = validator;
        this.passwordEncoder = passwordEncoder;
        this.externalUrl = externalUrl;
        this.auditLog = auditLog;
        this.domainEventPublisher = domainEventPublisher;
        this.securitySettings = securitySettings;
    }

    private static Map<String, Object> totpSnapshot(User user) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("username", user.getMetadata().getName());
        snapshot.put("uid", user.getSpec().getUid());
        snapshot.put("twoFactorAuthEnabled", user.getSpec().getTwoFactorAuthEnabled());
        snapshot.put("hasTotpSecret", StringUtils.isNotBlank(user.getSpec().getTotpEncryptedSecret()));
        return snapshot;
    }

    private static Map<String, Object> totpPayload(User user) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("twoFactorAuthEnabled", user.getSpec().getTwoFactorAuthEnabled());
        return payload;
    }

    private static String traceFlowId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE);
    }

    private static boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "TwoFactorAuthV1alpha1";
        return route().nest(
                        path("/authentications/two-factor"),
                        () -> route().GET(
                                        "/settings",
                                        this::getTwoFactorSettings,
                                        builder -> builder.operationId("GetTwoFactorAuthenticationSettings")
                                                .tag(tag)
                                                .description("Get Two-factor authentication settings.")
                                                .response(
                                                        responseBuilder().implementation(TwoFactorAuthSettings.class)))
                                .PUT(
                                        "/settings/enabled",
                                        this::enableTwoFactor,
                                        builder -> builder.operationId("EnableTwoFactor")
                                                .tag(tag)
                                                .description("Enable Two-factor authentication")
                                                .requestBody(
                                                        requestBodyBuilder().implementation(PasswordRequest.class))
                                                .response(
                                                        responseBuilder().implementation(TwoFactorAuthSettings.class)))
                                .PUT(
                                        "/settings/disabled",
                                        this::disableTwoFactor,
                                        builder -> builder.operationId("DisableTwoFactor")
                                                .tag(tag)
                                                .description("Disable Two-factor authentication")
                                                .requestBody(
                                                        requestBodyBuilder().implementation(PasswordRequest.class))
                                                .response(
                                                        responseBuilder().implementation(TwoFactorAuthSettings.class)))
                                .POST(
                                        "/totp",
                                        this::configureTotp,
                                        builder -> builder.operationId("ConfigurerTotp")
                                                .tag(tag)
                                                .description("Configure a TOTP")
                                                .requestBody(
                                                        requestBodyBuilder().implementation(TotpRequest.class))
                                                .response(
                                                        responseBuilder().implementation(TwoFactorAuthSettings.class)))
                                .DELETE(
                                        "/totp/-",
                                        this::deleteTotp,
                                        builder -> builder.operationId("DeleteTotp")
                                                .tag(tag)
                                                .description("Delete the current user's configured TOTP authenticator "
                                                        + "after password verification.")
                                                .requestBody(
                                                        requestBodyBuilder().implementation(PasswordRequest.class))
                                                .response(
                                                        responseBuilder().implementation(TwoFactorAuthSettings.class)))
                                .GET(
                                        "/totp/auth-link",
                                        this::getTotpAuthLink,
                                        builder -> builder.operationId("GetTotpAuthLink")
                                                .tag(tag)
                                                .description("Get TOTP auth link, including secret")
                                                .response(responseBuilder().implementation(TotpAuthLinkResponse.class)))
                                .build())
                .build();
    }

    private Mono<ServerResponse> deleteTotp(ServerRequest request) {
        var totpDeleteRequestMono = request.bodyToMono(PasswordRequest.class)
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("请求体不能为空")))
                .delayUntil(passwordRequest -> this.validateRequest(passwordRequest, "passwordRequest", request));

        var twoFactorAuthSettings = totpDeleteRequestMono.flatMap(passwordRequest -> getCurrentUser()
                .filter(user -> {
                    var rawPassword = passwordRequest.getPassword();
                    var encodedPassword = user.getSpec().getPassword();
                    return this.passwordEncoder.matches(rawPassword, encodedPassword);
                })
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("密码不匹配。")))
                .flatMap(user -> consumeTotpIfConfigured(user, passwordRequest.getTotpCode()))
                .doOnNext(user -> {
                    var spec = user.getSpec();
                    spec.setTotpEncryptedSecret(null);
                })
                .flatMap(client::update)
                .flatMap(user -> auditLog
                        .recordChild(
                                request.exchange(),
                                user.getSpec().getUid(),
                                "TOTP_UNBIND",
                                "User",
                                user.getMetadata().getName(),
                                "SUCCESS",
                                totpSnapshot(user),
                                null)
                        .thenReturn(user))
                .doOnNext(user -> domainEventPublisher.publish(
                        "user.totp.disabled", totpPayload(user), traceFlowId(request.exchange())))
                .map(TwoFactorUtils::getTwoFactorAuthSettings));
        return ServerResponse.ok().body(twoFactorAuthSettings, TwoFactorAuthSettings.class);
    }

    private Mono<ServerResponse> disableTwoFactor(ServerRequest request) {
        return toggleTwoFactor(request, false);
    }

    private Mono<ServerResponse> enableTwoFactor(ServerRequest request) {
        return toggleTwoFactor(request, true);
    }

    private Mono<ServerResponse> toggleTwoFactor(ServerRequest request, boolean enabled) {
        return request.bodyToMono(PasswordRequest.class)
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("请求体不能为空")))
                .delayUntil(passwordRequest -> this.validateRequest(passwordRequest, "passwordRequest", request))
                .flatMap(passwordRequest -> getCurrentUser()
                        .filter(user -> {
                            var encodedPassword = user.getSpec().getPassword();
                            var rawPassword = passwordRequest.getPassword();
                            return passwordEncoder.matches(rawPassword, encodedPassword);
                        })
                        .switchIfEmpty(Mono.error(
                                () -> new UnsatisfiedAttributeValueException("密码不匹配。")))
                        .flatMap(user -> consumeTotpIfConfigured(user, passwordRequest.getTotpCode()))
                        .doOnNext(user -> user.getSpec().setTwoFactorAuthEnabled(enabled))
                        .flatMap(client::update)
                        .map(TwoFactorUtils::getTwoFactorAuthSettings))
                .flatMap(twoFactorAuthSettings -> ServerResponse.ok().bodyValue(twoFactorAuthSettings));
    }

    private Mono<ServerResponse> getTotpAuthLink(ServerRequest request) {
        var authLinkResponse = getCurrentUser().map(user -> {
            var username = user.getMetadata().getName();
            var url = externalUrl.getURL(request.exchange().getRequest());
            var authority = url.getAuthority();
            var authKeyId = username + ":" + authority;
            var rawSecret = totpSecrets.generateTotpSecret();
            var authLink = UriComponentsBuilder.fromUriString("otpauth://totp")
                    .path(authKeyId)
                    .queryParam("secret", rawSecret)
                    .queryParam("issuer", securitySettings.totpIssuer())
                    .queryParam("digits", 6)
                    .build()
                    .toUri();
            var authLinkResp = new TotpAuthLinkResponse();
            authLinkResp.setAuthLink(authLink);
            authLinkResp.setRawSecret(rawSecret);
            return authLinkResp;
        });

        return ServerResponse.ok().body(authLinkResponse, TotpAuthLinkResponse.class);
    }

    private Mono<ServerResponse> configureTotp(ServerRequest request) {
        var totpRequestMono = request.bodyToMono(TotpRequest.class)
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("请求体不能为空。")))
                .delayUntil(totpRequest -> this.validateRequest(totpRequest, "totp", request));

        var configuredUser = totpRequestMono.flatMap(totpRequest -> {

            return getCurrentUser()
                    .filter(user -> {
                        var encodedPassword = user.getSpec().getPassword();
                        var rawPassword = totpRequest.getPassword();
                        return passwordEncoder.matches(rawPassword, encodedPassword);
                    })
                    .switchIfEmpty(Mono.error(
                            () -> new UnsatisfiedAttributeValueException("密码不匹配。")))
                    .flatMap(user -> consumeTotpIfConfigured(user, totpRequest.getCurrentTotpCode()))
                    .flatMap(user -> parseTotpCode(totpRequest.getCode())
                            .map(code -> {
                                var encryptedSecret = totpSecrets.encryptSecret(totpRequest.getSecret());
                                var step = totpConsumptions.matchEnrollmentStep(
                                        totpRequest.getSecret(), code);
                                user.getSpec().setTotpEncryptedSecret(encryptedSecret);
                                user.getSpec().setTotpLastStep(step);
                                return user;
                            })
                            .onErrorMap(
                                    TwoFactorAuthException.class,
                                    error -> new UnsatisfiedAttributeValueException(error.getMessage())))
                    .flatMap(client::update)
                    .flatMap(user -> auditLog
                            .recordChild(
                                    request.exchange(),
                                    user.getSpec().getUid(),
                                    "TOTP_BIND",
                                    "User",
                                    user.getMetadata().getName(),
                                    "SUCCESS",
                                    totpSnapshot(user),
                                    null)
                            .thenReturn(user))
                    .doOnNext(user -> domainEventPublisher.publish(
                            "user.totp.enabled", totpPayload(user), traceFlowId(request.exchange())));
        });

        var twoFactorAuthSettings = configuredUser.map(TwoFactorUtils::getTwoFactorAuthSettings);

        return ServerResponse.ok().body(twoFactorAuthSettings, TwoFactorAuthSettings.class);
    }

    private Mono<Void> validateRequest(Object target, String name, ServerRequest request) {
        var bindingResult = ValidationUtils.validate(target, name, validator);
        if (bindingResult.hasErrors()) {
            return Mono.error(new RequestBodyValidationException(bindingResult));
        }
        return Mono.empty();
    }

    private Mono<User> consumeTotpIfConfigured(User user, String totpCode) {
        if (StringUtils.isBlank(user.getSpec().getTotpEncryptedSecret())) {
            return Mono.just(user);
        }
        return parseTotpCode(totpCode)
                .flatMap(code -> totpConsumptions.consume(user, code))
                .onErrorMap(
                        TwoFactorAuthException.class,
                        error -> new UnsatisfiedAttributeValueException(error.getMessage()));
    }

    private Mono<Integer> parseTotpCode(String totpCode) {
        if (StringUtils.isBlank(totpCode)) {
            return Mono.error(new UnsatisfiedAttributeValueException("请输入双重验证码。"));
        }
        try {
            return Mono.just(Integer.parseInt(totpCode));
        } catch (NumberFormatException e) {
            return Mono.error(new UnsatisfiedAttributeValueException("双重验证码无效，请重试。"));
        }
    }

    private Mono<ServerResponse> getTwoFactorSettings(ServerRequest request) {
        return getCurrentUser()
                .map(TwoFactorUtils::getTwoFactorAuthSettings)
                .flatMap(settings -> ServerResponse.ok().bodyValue(settings));
    }

    private Mono<User> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(TwoFactorAuthEndpoint::isAuthenticatedUser)
                .switchIfEmpty(Mono.error(AccessDeniedException::new))
                .map(Authentication::getName)
                .flatMap(accounts::getUser);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    @Data
    public static class PasswordRequest {


        @Schema(requiredMode = REQUIRED)
        @NotBlank
        private String password;


        private String totpCode;
    }

    @Data
    public static class TotpAuthLinkResponse {


        @Schema(requiredMode = REQUIRED)
        private URI authLink;


        @Schema(requiredMode = REQUIRED)
        private String rawSecret;
    }

    @Data
    public static class TotpRequest {


        @Schema(requiredMode = REQUIRED)
        @NotBlank
        private String secret;


        @Schema(requiredMode = REQUIRED)
        @NotNull
        private String code;


        @Schema(requiredMode = REQUIRED)
        @NotBlank
        private String password;


        private String currentTotpCode;
    }
}