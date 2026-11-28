package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.communal.constant.ProtocolConstants;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.UnauthorizedAuthenticationEntryPoint;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.oidc.authentication.ClientAuthenticationRequiredEntryPoint;
import cn.tolim.pudans.iam.protocol.oidc.context.OidcAuthorizationServerContextFilter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.GenericApplicationListenerAdapter;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.context.DelegatingApplicationListener;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.web.NimbusJwkSetEndpointFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.*;

import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityConfigUtils.getOptionalBean;
import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getApplicationServiceLoader;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationServerConfigurer extends
        AbstractHttpConfigurer<OAuth2AuthorizationServerConfigurer, HttpSecurity> {

    private final Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> configurers = createConfigurers();

    // Endpoint matcher
    private final List<EndpointMatcher> endpointMatchers = new ArrayList<>();
    private final UserAgentParser userAgentParser;

    public OAuth2AuthorizationServerConfigurer(UserAgentParser userAgentParser) {
        this.userAgentParser = userAgentParser;
    }

    private static void initSessionRegistry(HttpSecurity httpSecurity) {
        SessionRegistry sessionRegistry = getOptionalBean(httpSecurity, SessionRegistry.class);
        if (sessionRegistry == null) {
            sessionRegistry = new SessionRegistryImpl();
            registerDelegateApplicationListener(httpSecurity,
                    (SessionRegistryImpl) sessionRegistry);
        }
        httpSecurity.setSharedObject(SessionRegistry.class, sessionRegistry);
    }

    public static JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        Set<JWSAlgorithm> jwsAlgs = new HashSet<>();
        jwsAlgs.addAll(JWSAlgorithm.Family.RSA);
        jwsAlgs.addAll(JWSAlgorithm.Family.EC);
        jwsAlgs.addAll(JWSAlgorithm.Family.HMAC_SHA);
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> jwsKeySelector = new JWSVerificationKeySelector<>(jwsAlgs,
                jwkSource);
        jwtProcessor.setJWSKeySelector(jwsKeySelector);
        // Override the default Nimbus claims set verifier as NimbusJwtDecoder handles it instead
        jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> {
        });
        return new NimbusJwtDecoder(jwtProcessor);
    }

    private static void registerDelegateApplicationListener(HttpSecurity httpSecurity,
                                                            ApplicationListener<?> delegate) {
        DelegatingApplicationListener delegatingApplicationListener = getOptionalBean(httpSecurity,
                DelegatingApplicationListener.class);
        if (delegatingApplicationListener == null) {
            return;
        }
        SmartApplicationListener smartListener = new GenericApplicationListenerAdapter(delegate);
        delegatingApplicationListener.addListener(smartListener);
    }

    public RequestMatcher getEndpointsMatcher() {
        // Return a deferred RequestMatcher
        // since endpointsMatcher is constructed in init(HttpSecurity).
        return request -> new OrRequestMatcher(
                endpointMatchers.stream().map(EndpointMatcher::getRequestMatcher).toList())
                .matches(request);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        initSessionRegistry(httpSecurity);
        this.configurers.values().forEach(configurer -> {
            configurer.init(httpSecurity);
            this.endpointMatchers.add(configurer.getEndpointMatcher());
        });
        // Get jwk endpoint
        this.endpointMatchers.add(new EndpointMatcher(
                new AntPathRequestMatcher(ProtocolConstants.OidcEndpointConstants.JWK_SET_ENDPOINT,
                        HttpMethod.GET.name()),
                false));
        // Exception handling
        httpSecurity.exceptionHandling(exceptionHandling -> {
            if (exceptionHandling != null) {
                // Authentication endpoint exception
                exceptionHandling.defaultAuthenticationEntryPointFor(
                        new UnauthorizedAuthenticationEntryPoint(userAgentParser), new OrRequestMatcher(
                                getRequestMatcher(OAuth2AuthorizationEndpointConfigurer.class)));
                exceptionHandling.defaultAuthenticationEntryPointFor(
                        new ClientAuthenticationRequiredEntryPoint(userAgentParser),
                        new OrRequestMatcher(getRequestMatcher(OAuth2TokenEndpointConfigurer.class),
                                getRequestMatcher(OAuth2TokenIntrospectionEndpointConfigurer.class),
                                getRequestMatcher(OAuth2TokenRevocationEndpointConfigurer.class),
                                getRequestMatcher(OAuth2DeviceAuthorizationEndpointConfigurer.class)));
            }
        });
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        // OIDC authorization server context filter
        ApplicationServiceLoader applicationServiceLoader = getApplicationServiceLoader(httpSecurity);
        OidcAuthorizationServerContextFilter oidcAuthorizationServerContextFilter = new OidcAuthorizationServerContextFilter(this.endpointMatchers, applicationServiceLoader);
        httpSecurity.addFilterAfter(postProcess(oidcAuthorizationServerContextFilter), SecurityContextHolderFilter.class);
        this.configurers.values().forEach(configurer -> configurer.configure(httpSecurity));
        // Jwk filter
        NimbusJwkSetEndpointFilter jwkSetEndpointFilter = new NimbusJwkSetEndpointFilter(OAuth2ConfigurerUtils.getJwkSource(httpSecurity), ProtocolConstants.OidcEndpointConstants.JWK_SET_ENDPOINT);
        httpSecurity.addFilterBefore(postProcess(jwkSetEndpointFilter), AbstractPreAuthenticatedProcessingFilter.class);
    }

    private <T extends AbstractConfigurer> RequestMatcher getRequestMatcher(Class<T> configurerType) {
        T configurer = getConfigurer(configurerType);
        return configurer != null ? configurer.getEndpointMatcher().getRequestMatcher() : null;
    }

    private <T> T getConfigurer(Class<T> type) {
        return (T) this.configurers.get(type);
    }

    // createConfigurers
    // Returns AbstractConfigurer
    private Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> createConfigurers() {
        Map<Class<? extends AbstractConfigurer>, AbstractConfigurer> configurers = new LinkedHashMap<>();
        // Client authentication endpoint
        configurers.put(OAuth2ClientAuthenticationConfigurer.class, new OAuth2ClientAuthenticationConfigurer(this::postProcess));
        // Authorization endpoint
        configurers.put(OAuth2AuthorizationEndpointConfigurer.class, new OAuth2AuthorizationEndpointConfigurer(this::postProcess));
        // Token endpoint
        configurers.put(OAuth2TokenEndpointConfigurer.class, new OAuth2TokenEndpointConfigurer(this::postProcess));
        // Token introspection endpoint configuration
        configurers.put(OAuth2TokenIntrospectionEndpointConfigurer.class, new OAuth2TokenIntrospectionEndpointConfigurer(this::postProcess));
        // Token revocation endpoint configuration
        configurers.put(OAuth2TokenRevocationEndpointConfigurer.class, new OAuth2TokenRevocationEndpointConfigurer(this::postProcess));
        // Device authorization endpoint configuration
        configurers.put(OAuth2DeviceAuthorizationEndpointConfigurer.class, new OAuth2DeviceAuthorizationEndpointConfigurer(this::postProcess));
        // Device verification endpoint configuration
        configurers.put(OAuth2DeviceVerificationEndpointConfigurer.class, new OAuth2DeviceVerificationEndpointConfigurer(this::postProcess));
        // OIDC provider endpoint configuration
        configurers.put(OidcProviderConfigurationEndpointConfigurer.class, new OidcProviderConfigurationEndpointConfigurer(this::postProcess));
        // OIDC logout endpoint configuration
        configurers.put(OidcLogoutEndpointConfigurer.class, new OidcLogoutEndpointConfigurer(this::postProcess));
        // UserInfo endpoint configuration
        configurers.put(OidcUserInfoEndpointConfigurer.class, new OidcUserInfoEndpointConfigurer(this::postProcess));
        return configurers;
    }
}
