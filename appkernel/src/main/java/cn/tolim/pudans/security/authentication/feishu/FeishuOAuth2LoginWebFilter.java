package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.security.authentication.ExternalLoginCompletionService;
import cn.tolim.pudans.security.authentication.LoginRejectedException;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService.FeishuSettings;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;


@Slf4j
@RequiredArgsConstructor
public class FeishuOAuth2LoginWebFilter implements WebFilter {

    private static final String AUTH_API_PREFIX = ApiGroups.API_PREFIX;
    static final String AUTHORIZATION_PATH = AUTH_API_PREFIX + "/oauth2/authorization/feishu";
    static final String CALLBACK_PATH = AUTH_API_PREFIX + "/oauth2/callback/feishu";
    static final String STATE_ATTRIBUTE = FeishuOAuth2LoginWebFilter.class.getName() + ".STATE";
    static final String BIND_ATTRIBUTE = FeishuOAuth2LoginWebFilter.class.getName() + ".BIND";
    private static final String BIND_QUERY_PARAMETER = "bind";
    private static final String BIND_RESULT_PATH = "/identity-providers";
    private final SecurityParameterSettingService securitySettings;

    private final FeishuClient feishuClient;

    private final FeishuUserService feishuUserService;

    private final ExternalLoginCompletionService externalLoginCompletionService;

    private final DomainEventPublisher domainEventPublisher;

    private final SecurityProperties securityProperties;

    private final ServerResponse.Context context;

    private static Mono<Void> redirect(ServerWebExchange exchange, String location) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        if (AUTHORIZATION_PATH.equals(path)) {
            return authorize(exchange);
        }
        if (CALLBACK_PATH.equals(path)) {
            return callback(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> authorize(ServerWebExchange exchange) {
        var properties = securitySettings.feishu();
        if (!properties.isConfigured()) {
            return loginFailure(exchange, unavailableReason(properties));
        }
        var bind = "true".equalsIgnoreCase(
                exchange.getRequest().getQueryParams().getFirst(BIND_QUERY_PARAMETER));
        if (!bind) {
            return buildAuthorizationRedirect(exchange, properties, false);
        }
        return currentUsername(exchange)
                .flatMap(username ->
                        buildAuthorizationRedirect(exchange, properties, true).thenReturn(Boolean.TRUE))
                .switchIfEmpty(Mono.defer(() ->
                        loginFailure(exchange, "feishu_bind_login_required").thenReturn(Boolean.FALSE)))
                .then();
    }

    private Mono<Void> buildAuthorizationRedirect(
            ServerWebExchange exchange, FeishuSettings properties, boolean bind) {
        var state = UUID.randomUUID().toString();
        var redirectUri = resolveRedirectUri(exchange, properties.redirectUri());
        var location = UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("app_id", properties.appId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("scope", properties.scope())
                .build()
                .encode()
                .toUriString();
        return exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().put(STATE_ATTRIBUTE, state);
                    session.getAttributes().put(BIND_ATTRIBUTE, bind);
                })
                .then(redirect(exchange, location))
                .then(Mono.fromRunnable(() -> exchange.getAttributes()
                        .put(AuditService.OPERATION_ATTRIBUTE, bind ? "BIND" : "LOGIN")));
    }

    private Mono<Void> callback(ServerWebExchange exchange) {
        var properties = securitySettings.feishu();
        if (!properties.isConfigured()) {
            return loginFailure(exchange, unavailableReason(properties));
        }
        var code = exchange.getRequest().getQueryParams().getFirst("code");
        var state = exchange.getRequest().getQueryParams().getFirst("state");
        if (!StringUtils.hasText(code)) {
            return loginFailure(exchange, "feishu_code_missing");
        }
        return exchange.getSession()
                .flatMap(session -> {
                    var expectedState = (String) session.getAttributes().get(STATE_ATTRIBUTE);
                    session.getAttributes().remove(STATE_ATTRIBUTE);
                    var binding = Boolean.TRUE.equals(session.getAttributes().remove(BIND_ATTRIBUTE));
                    if (expectedState == null || !expectedState.equals(state)) {
                        return loginFailure(exchange, "feishu_state_invalid");
                    }
                    if (binding) {
                        return bindIdentity(exchange, code);
                    }
                    return feishuClient
                            .exchangeCode(code)
                            .flatMap(token -> feishuClient.fetchUserInfo(token.accessToken()))
                            .flatMap(userInfo -> feishuUserService
                                    .findConnection(userInfo.unionId())
                                    .switchIfEmpty(Mono.defer(() -> feishuUserService.findConnection(
                                            userInfo.openId())))
                                    .flatMap(connection -> feishuUserService.syncProfile(
                                                    connection.getSpec().getUsername(),
                                                    userInfo.name(),
                                                    userInfo.email(),
                                                    userInfo.phone(),
                                                    userInfo.avatar())
                                            .thenReturn(connection.getSpec().getUsername())))
                            .flatMap(username -> establish(exchange, username).thenReturn(Boolean.TRUE))
                            .switchIfEmpty(Mono.defer(() -> loginFailure(exchange, "feishu_not_bound")
                                    .thenReturn(Boolean.FALSE)))
                            .then()
                            .onErrorResume(
                                    LoginRejectedException.class,
                                    error -> loginRejected(exchange, error.getReason()))
                            .onErrorResume(error -> {
                                log.warn("飞书登录失败", error);
                                return loginFailure(exchange, "feishu_login_failed");
                            });
                });
    }

    private Mono<Void> bindIdentity(ServerWebExchange exchange, String code) {
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "BIND");
        return currentUsername(exchange)
                .flatMap(username -> feishuClient
                        .exchangeCode(code)
                        .flatMap(token -> feishuClient.fetchUserInfo(token.accessToken()))
                        .flatMap(userInfo -> feishuUserService.bindConnection(
                                username,
                                providerUserId(userInfo),
                                userInfo.name(),
                                userInfo.email(),
                                userInfo.phone(),
                                userInfo.avatar()))
                        .then(redirect(exchange, BIND_RESULT_PATH + "?bound=feishu"))
                        .onErrorResume(error -> bindFailure(exchange, error))
                        .thenReturn(Boolean.TRUE))
                .switchIfEmpty(Mono.defer(() -> ProblemDetailResponses.writeOrRedirect(
                                exchange,
                                context,
                                HttpStatus.UNAUTHORIZED,
                                "feishu_bind_login_required",
                                "请先登录后再绑定飞书。",
                                ProblemDetailResponses.SIGN_IN_PATH)
                        .thenReturn(Boolean.FALSE)))
                .then();
    }

    private Mono<Void> bindFailure(ServerWebExchange exchange, Throwable error) {
        log.warn("飞书身份绑定失败", error);
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "DENIED");
        var message = StringUtils.hasText(error.getMessage()) ? error.getMessage() : "请稍后重试。";
        return ProblemDetailResponses.writeOrRedirect(
                exchange,
                context,
                HttpStatus.BAD_REQUEST,
                "feishu_bind_failed",
                "飞书身份绑定失败：" + message,
                BIND_RESULT_PATH);
    }

    private static String providerUserId(FeishuClient.FeishuUserInfo userInfo) {
        return StringUtils.hasText(userInfo.unionId()) ? userInfo.unionId() : userInfo.openId();
    }

    private static Mono<String> currentUsername(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(authentication -> authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken))
                .map(Authentication::getName);
    }

    private Mono<Void> establish(ServerWebExchange exchange, String username) {
        return externalLoginCompletionService
                .complete(exchange, username, "feishu")
                .flatMap(result -> result.twoFactorRequired()
                        ? twoFactorRequired(exchange)
                        : redirect(exchange, securityProperties.getPostLoginUrl()));
    }

    private Mono<Void> twoFactorRequired(ServerWebExchange exchange) {
        return ProblemDetailResponses.writeOrRedirect(
                exchange,
                context,
                HttpStatus.UNAUTHORIZED,
                "two-factor-required",
                "该账号需要完成双重验证，请使用密码登录。",
                ProblemDetailResponses.SIGN_IN_PATH);
    }

    private Mono<Void> loginRejected(ServerWebExchange exchange, String reason) {
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "DENIED");
        exchange.getAttributes().put(AuditService.CONTEXT_ATTRIBUTE, reason);
        return ProblemDetailResponses.writeOrRedirect(
                exchange,
                context,
                HttpStatus.FORBIDDEN,
                reason,
                rejectionDetail(reason),
                ProblemDetailResponses.SIGN_IN_PATH);
    }

    private String unavailableReason(FeishuSettings settings) {
        return settings.enabled() ? "feishu_not_configured" : "feishu_disabled";
    }

    private Mono<Void> loginFailure(ServerWebExchange exchange, String reason) {
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "DENIED");
        exchange.getAttributes().put(AuditService.CONTEXT_ATTRIBUTE, reason);
        publishLoginFailure(exchange, reason);
        return ProblemDetailResponses.writeOrRedirect(
                exchange,
                context,
                HttpStatus.UNAUTHORIZED,
                reason,
                failureDetail(reason),
                ProblemDetailResponses.SIGN_IN_PATH);
    }

    private String failureDetail(String reason) {
        return switch (reason) {
            case "feishu_not_bound" -> "该飞书账号尚未绑定 Pudans 账号，请先使用账号密码登录后绑定飞书。";
            case "feishu_bind_login_required" -> "请先登录后再绑定飞书。";
            case "feishu_state_invalid" -> "登录会话已失效，请重新发起飞书登录。";
            case "feishu_code_missing" -> "飞书授权回调缺少授权码，请重新登录。";
            case "feishu_login_failed" -> "飞书登录失败，请稍后重试。";
            case "feishu_not_configured" -> "飞书登录尚未配置。";
            case "feishu_disabled" -> "飞书登录未启用。";
            default -> "飞书登录失败。";
        };
    }

    private String rejectionDetail(String reason) {
        return switch (reason) {
            case "account_not_found" -> "未找到与该飞书账号关联的 Pudans 账号，请先使用账号密码登录后绑定飞书。";
            case "account_disabled" -> "该账号已停用，请联系管理员。";
            case "account_locked" -> "该账号已锁定，请联系管理员。";
            case "account_expired" -> "该账号已过期，请联系管理员。";
            case "credentials_expired" -> "该账号的登录凭据已过期，请重置密码。";
            default -> "飞书登录被拒绝。";
        };
    }

    private void publishLoginFailure(ServerWebExchange exchange, String reason) {
        var username = (String) exchange.getAttributes().get(AuditService.USERNAME_ATTRIBUTE);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", username == null ? "anonymous" : username);
        payload.put("method", "feishu");
        payload.put("reason", reason);
        payload.put("ip", IpAddressUtils.getClientIp(exchange.getRequest()));
        domainEventPublisher.publish(
                "user.login.failed",
                payload,
                (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private String resolveRedirectUri(ServerWebExchange exchange, String configured) {
        var requestUri = exchange.getRequest().getURI();
        var baseUrl = UriComponentsBuilder.fromUri(requestUri)
                .replacePath("")
                .replaceQuery(null)
                .build()
                .toUriString();
        if (configured.contains("{baseUrl}")) {
            return configured.replace("{baseUrl}", baseUrl);
        }
        return configured;
    }
}
