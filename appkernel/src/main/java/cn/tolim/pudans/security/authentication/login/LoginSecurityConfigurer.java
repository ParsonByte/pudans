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
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.service.RefreshTokenService;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.LoginFlowHandler;
import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConstant;
import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import cn.tolim.pudans.security.authentication.LoginFailureHandler;
import cn.tolim.pudans.security.authentication.LoginSuccessHandler;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import cn.tolim.pudans.security.captcha.CaptchaService;

@Component
@Order(0)
public class LoginSecurityConfigurer implements SecurityConfigurer {

    private final ObservationRegistry observationRegistry;

    private final ReactiveUserDetailsService userDetailsService;

    private final ReactiveUserDetailsPasswordService passwordService;

    private final UserService userService;

    private final PasswordEncoder passwordEncoder;

    private final ServerSecurityContextRepository securityContextRepository;

    private final CryptoService cryptoService;

    private final ServerResponse.Context context;

    private final RiskControlService riskControlService;

    private final LoginFlowHandler loginFlowHandler;

    private final LoginParameterRequestCache parameterRequestCache;

    private final DomainEventPublisher domainEventPublisher;

    private final ServerRequestCache serverRequestCache;

    private final SecurityProperties securityProperties;

    private final CaptchaService captchaService;

    private final RefreshTokenService refreshTokenService;

    private final SecurityParameterSettingService securitySettings;

    public LoginSecurityConfigurer(
            ObservationRegistry observationRegistry,
            ReactiveUserDetailsService userDetailsService,
            ReactiveUserDetailsPasswordService passwordService,
            UserService userService,
            PasswordEncoder passwordEncoder,
            ServerSecurityContextRepository securityContextRepository,
            CryptoService cryptoService,
            ServerResponse.Context context,
            RiskControlService riskControlService,
            LoginFlowHandler loginFlowHandler,
            LoginParameterRequestCache parameterRequestCache,
            DomainEventPublisher domainEventPublisher,
            ServerRequestCache serverRequestCache,
            SecurityProperties securityProperties,
            CaptchaService captchaService,
            RefreshTokenService refreshTokenService,
            SecurityParameterSettingService securitySettings) {
        this.observationRegistry = observationRegistry;
        this.userDetailsService = userDetailsService;
        this.passwordService = passwordService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.cryptoService = cryptoService;
        this.context = context;
        this.riskControlService = riskControlService;
        this.loginFlowHandler = loginFlowHandler;
        this.parameterRequestCache = parameterRequestCache;
        this.domainEventPublisher = domainEventPublisher;
        this.serverRequestCache = serverRequestCache;
        this.securityProperties = securityProperties;
        this.captchaService = captchaService;
        this.refreshTokenService = refreshTokenService;
        this.securitySettings = securitySettings;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        var filter = new AuthenticationWebFilter(authenticationManager()) {
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
                cryptoService, riskControlService, captchaService, securitySettings);
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
                        refreshTokenService));
        filter.setAuthenticationFailureHandler(
                new LoginFailureHandler("local", context, loginFlowHandler, domainEventPublisher));
        filter.setServerAuthenticationConverter(authConverter);
        filter.setSecurityContextRepository(securityContextRepository);

        http.addFilterAt(filter, SecurityWebFiltersOrder.FORM_LOGIN);
    }

    ReactiveAuthenticationManager authenticationManager() {
        return new ObservationReactiveAuthenticationManager(observationRegistry, defaultAuthenticationManager());
    }

    ReactiveAuthenticationManager defaultAuthenticationManager() {
        return new LoginReactiveAuthenticationManager(
                userDetailsService, userService, passwordEncoder, passwordService);
    }
}
