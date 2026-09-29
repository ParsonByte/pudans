package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.contract.security.LoginHandlerEnhancer;
import cn.tolim.pudans.core.audit.service.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import reactor.core.publisher.Mono;

@Slf4j
public class TotpAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final LoginHandlerEnhancer loginEnhancer;

    private final ServerAuthenticationSuccessHandler successHandler;

    private final AuditService auditService;

    public TotpAuthenticationSuccessHandler(
            LoginHandlerEnhancer loginEnhancer, ServerRequestCache serverRequestCache, AuditService auditService) {
        this.loginEnhancer = loginEnhancer;
        this.auditService = auditService;
        var successHandler = new RedirectServerAuthenticationSuccessHandler("/uc");
        successHandler.setRequestCache(serverRequestCache);
        this.successHandler = successHandler;
    }

    private static Map<String, Object> snapshot(Authentication authentication) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("username", authentication.getName());
        snapshot.put("method", "totp");
        snapshot.put("authorities", authentication.getAuthorities().stream()
                .map(Object::toString)
                .toList());
        return snapshot;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        var exchange = webFilterExchange.getExchange();
        return loginEnhancer
                .onLoginSuccess(exchange, authentication)
                .then(Mono.defer(() -> auditService.recordChild(
                        exchange,
                        null,
                        "TOTP_LOGIN_SUCCESS",
                        "User",
                        authentication.getName(),
                        "SUCCESS",
                        snapshot(authentication),
                        null)))
                .then(successHandler.onAuthenticationSuccess(webFilterExchange, authentication));
    }
}
