package cn.tolim.pudans.security.authentication.twofactor;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import cn.tolim.pudans.contract.security.LoginHandlerEnhancer;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.risk.service.RiskControlService;
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

    private final LoginHandlerEnhancer loginHandlerEnhancer;

    private final ServerRequestCache serverRequestCache;

    private final RiskControlService riskControlService;

    private final TotpAuthenticationFailureHandler failureHandler;

    private final AuditService auditService;

    private final SecurityProperties securityProperties;

    public TwoFactorAuthSecurityConfigurer(
            ServerSecurityContextRepository securityContextRepository,
            TwoFactorAuthenticationManager twoFactorAuthenticationManager,
            LoginHandlerEnhancer loginHandlerEnhancer,
            ServerRequestCache serverRequestCache,
            RiskControlService riskControlService,
            TotpAuthenticationFailureHandler failureHandler,
            AuditService auditService,
            SecurityProperties securityProperties) {
        this.securityContextRepository = securityContextRepository;
        this.twoFactorAuthenticationManager = twoFactorAuthenticationManager;
        this.loginHandlerEnhancer = loginHandlerEnhancer;
        this.serverRequestCache = serverRequestCache;
        this.riskControlService = riskControlService;
        this.failureHandler = failureHandler;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        var filter = new AuthenticationWebFilter(twoFactorAuthenticationManager);
        filter.setRequiresAuthenticationMatcher(pathMatchers(HttpMethod.POST, "/challenges/two-factor/*"));
        filter.setSecurityContextRepository(securityContextRepository);
        filter.setServerAuthenticationConverter(new TwoFactorCodeAuthenticationConverter(riskControlService));
        filter.setAuthenticationSuccessHandler(
                new TotpAuthenticationSuccessHandler(
                        loginHandlerEnhancer,
                        serverRequestCache,
                        auditService,
                        securityProperties.getPostLoginUrl()));
        filter.setAuthenticationFailureHandler(failureHandler);
        http.addFilterAt(filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
