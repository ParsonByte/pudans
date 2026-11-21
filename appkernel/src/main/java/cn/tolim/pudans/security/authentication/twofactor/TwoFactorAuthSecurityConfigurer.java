package cn.tolim.pudans.security.authentication.twofactor;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.stereotype.Component;


@Component
@Order(0)
public class TwoFactorAuthSecurityConfigurer implements SecurityConfigurer {

    private final ServerSecurityContextRepository securityContextRepository;

    private final TwoFactorAuthenticationManager twoFactorAuthenticationManager;

    private final LoginFlowHandler loginFlowHandler;

    private final ServerRequestCache serverRequestCache;

    private final RiskGuard riskGuard;

    private final TotpAuthenticationFailureHandler failureHandler;

    private final AuditLog auditLog;

    private final SecurityProperties securityProperties;

    // 注入过滤器所需依赖，保证认证流程与全局登录逻辑一致。
    public TwoFactorAuthSecurityConfigurer(
            ServerSecurityContextRepository securityContextRepository,
            TwoFactorAuthenticationManager twoFactorAuthenticationManager,
            LoginFlowHandler loginFlowHandler,
            ServerRequestCache serverRequestCache,
            RiskGuard riskGuard,
            TotpAuthenticationFailureHandler failureHandler,
            AuditLog auditLog,
            SecurityProperties securityProperties) {
        this.securityContextRepository = securityContextRepository;
        this.twoFactorAuthenticationManager = twoFactorAuthenticationManager;
        this.loginFlowHandler = loginFlowHandler;
        this.serverRequestCache = serverRequestCache;
        this.riskGuard = riskGuard;
        this.failureHandler = failureHandler;
        this.auditLog = auditLog;
        this.securityProperties = securityProperties;
    }

    // 注册双重验证过滤器，仅拦截挑战端点并复用统一处理逻辑。
    @Override
    public void configure(ServerHttpSecurity http) {
        var filter = new AuthenticationWebFilter(twoFactorAuthenticationManager);
        filter.setRequiresAuthenticationMatcher(pathMatchers(HttpMethod.POST, "/challenges/two-factor/*"));
        filter.setSecurityContextRepository(securityContextRepository);
        filter.setServerAuthenticationConverter(new TwoFactorCodeAuthenticationConverter(riskGuard));
        filter.setAuthenticationSuccessHandler(
                new TotpAuthenticationSuccessHandler(
                        loginFlowHandler,
                        serverRequestCache,
                        auditLog,
                        securityProperties.getPostLoginUrl()));
        filter.setAuthenticationFailureHandler(failureHandler);
        http.addFilterAt(filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
