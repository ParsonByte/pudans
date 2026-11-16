package cn.tolim.pudans.security.authentication.twofactor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ExternalUrlSupplier;
import cn.tolim.pudans.contract.infra.ValidationUtils;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.exception.RequestBodyValidationException;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.security.authentication.twofactor.totp.TotpAuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
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

    private final UserService userService;

    private final TotpAuthService totpAuthService;

    private final Validator validator;

    private final PasswordEncoder passwordEncoder;

    private final ExternalUrlSupplier externalUrl;

    private final AuditService auditService;

    private final DomainEventPublisher domainEventPublisher;

    public TwoFactorAuthEndpoint(
            ReactiveExtensionClient client,
            UserService userService,
            TotpAuthService totpAuthService,
            Validator validator,
            PasswordEncoder passwordEncoder,
            ExternalUrlSupplier externalUrl,
            AuditService auditService,
            DomainEventPublisher domainEventPublisher) {
        this.client = client;
        this.userService = userService;
        this.totpAuthService = totpAuthService;
        this.validator = validator;
        this.passwordEncoder = passwordEncoder;
        this.externalUrl = externalUrl;
        this.auditService = auditService;
        this.domainEventPublisher = domainEventPublisher;
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
        return (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE);
    }

    private static boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "TwoFactorAuthV1alpha1Uc";
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
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("Request body required")))
                .delayUntil(passwordRequest -> this.validateRequest(passwordRequest, "passwordRequest", request));

        var twoFactorAuthSettings = totpDeleteRequestMono.flatMap(passwordRequest -> getCurrentUser()
                .filter(user -> {
                    var rawPassword = passwordRequest.getPassword();
                    var encodedPassword = user.getSpec().getPassword();
                    return this.passwordEncoder.matches(rawPassword, encodedPassword);
                })
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("problemDetail.user.password.notMatch")))
                .delayUntil(user -> validateTotpCode(user, passwordRequest.getTotpCode()))
                .doOnNext(user -> {
                    var spec = user.getSpec();
                    spec.setTotpEncryptedSecret(null);
                })
                .flatMap(client::update)
                .flatMap(user -> auditService
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
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("Request body required")))
                .delayUntil(passwordRequest -> this.validateRequest(passwordRequest, "passwordRequest", request))
                .flatMap(passwordRequest -> getCurrentUser()
                        .filter(user -> {
                            var encodedPassword = user.getSpec().getPassword();
                            var rawPassword = passwordRequest.getPassword();
                            return passwordEncoder.matches(rawPassword, encodedPassword);
                        })
                        .switchIfEmpty(Mono.error(
                                () -> new UnsatisfiedAttributeValueException("problemDetail.user.password.notMatch")))
                        .delayUntil(user -> validateTotpCode(user, passwordRequest.getTotpCode()))
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
            var rawSecret = totpAuthService.generateTotpSecret();
            var authLink = UriComponentsBuilder.fromUriString("otpauth://totp")
                    .path(authKeyId)
                    .queryParam("secret", rawSecret)
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
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("Request body required.")))
                .delayUntil(totpRequest -> this.validateRequest(totpRequest, "totp", request));

        var configuredUser = totpRequestMono.flatMap(totpRequest -> {

            return getCurrentUser()
                    .filter(user -> {
                        var encodedPassword = user.getSpec().getPassword();
                        var rawPassword = totpRequest.getPassword();
                        return passwordEncoder.matches(rawPassword, encodedPassword);
                    })
                    .switchIfEmpty(Mono.error(
                            () -> new UnsatisfiedAttributeValueException("problemDetail.user.password.notMatch")))
                    .delayUntil(user -> validateTotpCode(user, totpRequest.getCurrentTotpCode()))
                    .delayUntil(user -> {
                        var rawSecret = totpRequest.getSecret();
                        var encryptedSecret = totpAuthService.encryptSecret(rawSecret);
                        return validateTotpCode(encryptedSecret, totpRequest.getCode())
                                .then(Mono.fromRunnable(() -> user.getSpec().setTotpEncryptedSecret(encryptedSecret)));
                    })
                    .flatMap(client::update)
                    .flatMap(user -> auditService
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
        var bindingResult = ValidationUtils.validate(target, name, validator, request.exchange());
        if (bindingResult.hasErrors()) {
            return Mono.error(new RequestBodyValidationException(bindingResult));
        }
        return Mono.empty();
    }

    private Mono<Void> validateTotpCode(User user, String totpCode) {
        var totpEncryptedSecret = user.getSpec().getTotpEncryptedSecret();
        if (StringUtils.isBlank(totpEncryptedSecret)) {

            return Mono.empty();
        }
        return validateTotpCode(totpEncryptedSecret, totpCode);
    }

    private Mono<Void> validateTotpCode(String totpEncryptedSecret, String totpCode) {
        if (StringUtils.isBlank(totpCode)) {
            return Mono.error(new UnsatisfiedAttributeValueException("problemDetail.user.twoFactor.code.required"));
        }
        int code;
        try {
            code = Integer.parseInt(totpCode);
        } catch (NumberFormatException e) {
            return Mono.error(new UnsatisfiedAttributeValueException("problemDetail.user.twoFactor.code.invalid"));
        }
        var rawSecret = totpAuthService.decryptSecret(totpEncryptedSecret);
        if (!totpAuthService.validateTotp(rawSecret, code)) {
            return Mono.error(new UnsatisfiedAttributeValueException("problemDetail.user.twoFactor.code.invalid"));
        }
        return Mono.empty();
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
                .flatMap(userService::getUser);
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