package cn.tolim.pudans.security.authentication.login;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ObservationReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.RefreshTokens;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConstant;
import cn.tolim.pudans.security.contract.authentication.LoginCipher;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.LoginFailureHandler;
import cn.tolim.pudans.security.authentication.LoginSuccessHandler;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import cn.tolim.pudans.security.captcha.Captchas;

@Component
@Order(0)
public class LoginSecurityConfigurer implements SecurityConfigurer {

    private final ObservationRegistry observationRegistry;

    private final ReactiveUserDetailsService reactiveUserDetailsService;

    private final ReactiveUserDetailsPasswordService reactiveUserDetailsPasswordService;

    private final Accounts accounts;

    private final PasswordEncoder passwordEncoder;

    private final ServerSecurityContextRepository securityContextRepository;

    private final LoginCipher loginCipher;

    private final ServerResponse.Context context;

    private final RiskGuard riskGuard;

    private final LoginFlowHandler loginFlowHandler;

    private final LoginParameterRequestCache parameterRequestCache;

    private final DomainEventPublisher domainEventPublisher;

    private final ServerRequestCache serverRequestCache;

    private final SecurityProperties securityProperties;

    private final Captchas captchas;

    private final RefreshTokens refreshTokens;

    private final SecurityParameters securitySettings;

    // 汇聚表单登录所需的认证、会话与风控依赖。
    public LoginSecurityConfigurer(
            ObservationRegistry observationRegistry,
            ReactiveUserDetailsService reactiveUserDetailsService,
            ReactiveUserDetailsPasswordService reactiveUserDetailsPasswordService,
            Accounts accounts,
            PasswordEncoder passwordEncoder,
            ServerSecurityContextRepository securityContextRepository,
            LoginCipher loginCipher,
            ServerResponse.Context context,
            RiskGuard riskGuard,
            LoginFlowHandler loginFlowHandler,
            LoginParameterRequestCache parameterRequestCache,
            DomainEventPublisher domainEventPublisher,
            ServerRequestCache serverRequestCache,
            SecurityProperties securityProperties,
            Captchas captchas,
            RefreshTokens refreshTokens,
            SecurityParameters securitySettings) {
        this.observationRegistry = observationRegistry;
        this.reactiveUserDetailsService = reactiveUserDetailsService;
        this.reactiveUserDetailsPasswordService = reactiveUserDetailsPasswordService;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.loginCipher = loginCipher;
        this.context = context;
        this.riskGuard = riskGuard;
        this.loginFlowHandler = loginFlowHandler;
        this.parameterRequestCache = parameterRequestCache;
        this.domainEventPublisher = domainEventPublisher;
        this.serverRequestCache = serverRequestCache;
        this.securityProperties = securityProperties;
        this.captchas = captchas;
        this.refreshTokens = refreshTokens;
        this.securitySettings = securitySettings;
    }

    // 装配表单登录过滤器并标记双因素待验证身份。
    @Override
    public void configure(ServerHttpSecurity http) {
        var filter = new AuthenticationWebFilter(authenticationManager()) {
            // 双因素账号先转为待验证身份再交给成功处理器。
            @Override
            protected Mono<Void> onAuthenticationSuccess(
                    Authentication authentication, WebFilterExchange webFilterExchange) {
                if (authentication.getPrincipal() instanceof PudansUserDetailsContract userDetails
                        && userDetails.isTwoFactorAuthEnabled()) {
                    authentication = new TwoFactorAuthentication(authentication);
                }
                return super.onAuthenticationSuccess(authentication, webFilterExchange);
            }
        };
        var requiresMatcher = ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/login");
        var authConverter = new LoginAuthenticationConverter(
                loginCipher, riskGuard, captchas, securitySettings);
        authConverter.setUsernameParameter(SecurityConstant.USERNAME_PARAMETER_NAME);
        authConverter.setPasswordParameter(SecurityConstant.PASSWORD_PARAMETER_NAME);
        filter.setRequiresAuthenticationMatcher(requiresMatcher);
        filter.setAuthenticationSuccessHandler(
                new LoginSuccessHandler(
                        context,
                        loginFlowHandler,
                        parameterRequestCache,
                        serverRequestCache,
                        securityProperties.getPostLoginUrl(),
                        refreshTokens));
        filter.setAuthenticationFailureHandler(
                new LoginFailureHandler("local", context, loginFlowHandler, domainEventPublisher));
        filter.setServerAuthenticationConverter(authConverter);
        filter.setSecurityContextRepository(securityContextRepository);

        http.addFilterAt(filter, SecurityWebFiltersOrder.FORM_LOGIN);
    }

    // 为认证管理器补充观测埋点。
    ReactiveAuthenticationManager authenticationManager() {
        return new ObservationReactiveAuthenticationManager(observationRegistry, defaultAuthenticationManager());
    }

    // 构建基于用户详情与密码编码器的认证流程。
    ReactiveAuthenticationManager defaultAuthenticationManager() {
        return new LoginReactiveAuthenticationManager(
                reactiveUserDetailsService, accounts, passwordEncoder, reactiveUserDetailsPasswordService);
    }
}
