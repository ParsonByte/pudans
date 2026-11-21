package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.security.authentication.oauth2.OAuth2LoginAttributes;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import reactor.core.publisher.Mono;

public class TotpAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final LoginFlowHandler loginEnhancer;

    private final ServerAuthenticationSuccessHandler successHandler;

    private final AuditLog auditLog;

    // 构建登录后跳转处理器，并复用全局请求缓存恢复原访问页。
    public TotpAuthenticationSuccessHandler(
            LoginFlowHandler loginEnhancer,
            ServerRequestCache serverRequestCache,
            AuditLog auditLog,
            String postLoginUrl) {
        this.loginEnhancer = loginEnhancer;
        this.auditLog = auditLog;
        var successHandler = new RedirectServerAuthenticationSuccessHandler(postLoginUrl);
        successHandler.setRequestCache(serverRequestCache);
        this.successHandler = successHandler;
    }

    // 构造审计快照，记录用户名、认证方式与已授予的权限。
    private static Map<String, Object> snapshot(Authentication authentication) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("username", authentication.getName());
        snapshot.put("method", "totp");
        snapshot.put("authorities", authentication.getAuthorities().stream()
                .map(Object::toString)
                .toList());
        return snapshot;
    }

    // 若登录前存在授权的续跳地址，优先恢复该流程避免中断。
    private Mono<Boolean> resumeOAuth2(WebFilterExchange webFilterExchange) {
        var exchange = webFilterExchange.getExchange();
        return exchange.getSession()
                .flatMap(session -> {
                    var resumeUri = (String) session.getAttributes().remove(OAuth2LoginAttributes.RESUME_URI);
                    if (resumeUri == null) {
                        return Mono.just(false);
                    }
                    var response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().setLocation(URI.create(resumeUri));
                    return response.setComplete().thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    // 完成登录增强与审计后，优先恢复授权流程，否则按默认跳转。
    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        var exchange = webFilterExchange.getExchange();
        return loginEnhancer
                .onLoginSuccess(exchange, authentication)
                .then(Mono.defer(() -> auditLog.recordChild(
                        exchange,
                        null,
                        "TOTP_LOGIN_SUCCESS",
                        "User",
                        authentication.getName(),
                        "SUCCESS",
                        snapshot(authentication),
                        null)))
                .then(Mono.defer(() -> resumeOAuth2(webFilterExchange)
                        .flatMap(resumed -> Boolean.TRUE.equals(resumed)
                                ? Mono.empty()
                                : successHandler.onAuthenticationSuccess(webFilterExchange, authentication))));
    }
}
