package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitRequestAuthenticationProvider;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitRequestAuthenticationToken;
import cn.tolim.pudans.iam.protocol.oidc.authorization.client.OidcConfigRegisteredClientRepositoryWrapper;
import cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2AuthorizationEndpointFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.AUTHORIZATION_ENDPOINT;
import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getAuthenticationDetailsSource;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationEndpointConfigurer extends AbstractConfigurer {

    private RequestMatcher requestMatcher;

    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    // Restrict for internal use only.
    OAuth2AuthorizationEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        sessionAuthenticationStrategy = (authentication, request, response) -> {
            SessionRegistry sessionRegistry = httpSecurity.getSharedObject(SessionRegistry.class);
            // Authorization code request
            if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token) {
                if (token.getScopes().contains(OidcScopes.OPENID)) {
                    if (sessionRegistry
                            .getSessionInformation(request.getSession().getId()) == null) {
                        sessionRegistry.registerNewSession(request.getSession().getId(),
                                ((Authentication) token.getPrincipal()).getPrincipal());
                    }
                }
            }
            // Implicit mode
            if (authentication instanceof OAuth2AuthorizationImplicitRequestAuthenticationToken token) {
                if (token.getScopes().contains(OidcScopes.OPENID)) {
                    if (sessionRegistry
                            .getSessionInformation(request.getSession().getId()) == null) {
                        sessionRegistry.registerNewSession(request.getSession().getId(),
                                ((Authentication) token.getPrincipal()).getPrincipal());
                    }
                }
            }
        };

        this.requestMatcher = new OrRequestMatcher(
                new AntPathRequestMatcher(AUTHORIZATION_ENDPOINT, HttpMethod.GET.name()),
                new AntPathRequestMatcher(AUTHORIZATION_ENDPOINT, HttpMethod.POST.name()));

        List<AuthenticationProvider> authenticationProviders = createDefaultAuthenticationProviders(
                httpSecurity);
        authenticationProviders.forEach(authenticationProvider -> httpSecurity
                .authenticationProvider(postProcess(authenticationProvider)));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        AuthenticationManager authenticationManager = httpSecurity
                .getSharedObject(AuthenticationManager.class);
        OAuth2AuthorizationService authorizationService = httpSecurity
                .getSharedObject(OAuth2AuthorizationService.class);
        OAuth2AuthorizationEndpointFilter authorizationEndpointFilter = new OAuth2AuthorizationEndpointFilter(
                authorizationService, authenticationManager, AUTHORIZATION_ENDPOINT);
        // Session authentication policy
        authorizationEndpointFilter
                .setSessionAuthenticationStrategy(this.sessionAuthenticationStrategy);
        // Authentication detail source
        authorizationEndpointFilter
                .setAuthenticationDetailsSource(getAuthenticationDetailsSource(httpSecurity));

        httpSecurity.addFilterBefore(postProcess(authorizationEndpointFilter),
                AbstractPreAuthenticatedProcessingFilter.class);
    }

    private List<AuthenticationProvider> createDefaultAuthenticationProviders(HttpSecurity httpSecurity) {
        List<AuthenticationProvider> authenticationProviders = new ArrayList<>();
        OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator = OAuth2ConfigurerUtils
                .getTokenGenerator(httpSecurity);

        // Authorization code authentication
        OAuth2AuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider = new OAuth2AuthorizationCodeRequestAuthenticationProvider(
                new OidcConfigRegisteredClientRepositoryWrapper(
                        OAuth2ConfigurerUtils.getRegisteredClientRepository(httpSecurity)),
                OAuth2ConfigurerUtils.getAuthorizationService(httpSecurity),
                OAuth2ConfigurerUtils.getAuthorizationConsentService(httpSecurity));
        authenticationProviders.add(authorizationCodeRequestAuthenticationProvider);

        // Implicit mode authentication
        OAuth2AuthorizationImplicitRequestAuthenticationProvider authenticationImplicitRequestAuthenticationProvider = new OAuth2AuthorizationImplicitRequestAuthenticationProvider(
                new OidcConfigRegisteredClientRepositoryWrapper(
                        OAuth2ConfigurerUtils.getRegisteredClientRepository(httpSecurity)),
                OAuth2ConfigurerUtils.getAuthorizationService(httpSecurity),
                OAuth2ConfigurerUtils.getAuthorizationConsentService(httpSecurity), tokenGenerator);
        authenticationProviders.add(authenticationImplicitRequestAuthenticationProvider);

        return authenticationProviders;
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, true);
    }
}
