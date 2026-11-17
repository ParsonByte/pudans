package cn.tolim.pudans.security.authentication.feishu;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.security.authentication.ExternalLoginCompletionService;
import cn.tolim.pudans.security.authentication.LoginRejectedException;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.exception.Exceptions;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService.FeishuSettings;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthRequiredException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private static final String STATE_ATTRIBUTE = FeishuOAuth2LoginWebFilter.class.getName() + ".STATE";
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
                .doOnNext(session -> session.getAttributes().put(STATE_ATTRIBUTE, state))
                .then(redirect(exchange, location))
                .then(Mono.fromRunnable(() -> exchange.getAttributes()
                        .put(AuditService.OPERATION_ATTRIBUTE, "LOGIN")));
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
                    if (expectedState == null || !expectedState.equals(state)) {
                        return loginFailure(exchange, "feishu_state_invalid");
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
                                                    userInfo.email())
                                            .thenReturn(connection.getSpec().getUsername())))
                            .flatMap(username -> establish(exchange, username))
                            .switchIfEmpty(Mono.defer(() -> loginFailure(exchange, "feishu_not_bound")))
                            .onErrorResume(
                                    LoginRejectedException.class,
                                    error -> loginRejected(exchange, error.getReason()))
                            .onErrorResume(error -> {
                                log.warn("飞书登录失败", error);
                                return loginFailure(exchange, "feishu_login_failed");
                            });
                });
    }

    private Mono<Void> establish(ServerWebExchange exchange, String username) {
        return externalLoginCompletionService
                .complete(exchange, username, "feishu")
                .flatMap(result -> result.twoFactorRequired()
                        ? twoFactorRequired(exchange)
                        : redirect(exchange, securityProperties.getPostLoginUrl()));
    }

    private Mono<Void> twoFactorRequired(ServerWebExchange exchange) {
        var errorResponse = Exceptions.createErrorResponse(new TwoFactorAuthRequiredException(), null, exchange);
        return ServerResponse.status(errorResponse.getStatusCode())
                .bodyValue(errorResponse.getBody())
                .flatMap(response -> response.writeTo(exchange, context));
    }

    private Mono<Void> loginRejected(ServerWebExchange exchange, String reason) {
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "DENIED");
        exchange.getAttributes().put(AuditService.CONTEXT_ATTRIBUTE, reason);
        return ProblemDetailResponses.write(
                exchange, context, HttpStatus.FORBIDDEN, reason, "飞书登录被拒绝。");
    }

    private String unavailableReason(FeishuSettings settings) {
        return settings.enabled() ? "feishu_not_configured" : "feishu_disabled";
    }

    private Mono<Void> loginFailure(ServerWebExchange exchange, String reason) {
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "DENIED");
        exchange.getAttributes().put(AuditService.CONTEXT_ATTRIBUTE, reason);
        publishLoginFailure(exchange, reason);
        return ProblemDetailResponses.write(
                exchange, context, HttpStatus.UNAUTHORIZED, reason, "飞书登录失败。");
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
