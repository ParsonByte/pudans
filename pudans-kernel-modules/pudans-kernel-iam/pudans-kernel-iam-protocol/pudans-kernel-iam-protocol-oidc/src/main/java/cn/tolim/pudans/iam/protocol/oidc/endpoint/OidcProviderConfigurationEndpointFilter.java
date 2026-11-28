package cn.tolim.pudans.iam.protocol.oidc.endpoint;

import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.oidc.OidcProviderConfiguration;
import org.springframework.security.oauth2.server.authorization.oidc.http.converter.OidcProviderConfigurationHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.WELL_KNOWN_OPENID_CONFIGURATION;
import static cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitRequestAuthenticationProvider.IMPLICIT;
import static cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationResourceOwnerPasswordAuthenticationToken.PASSWORD;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationImplicitRequestAuthenticationConverter.ID_TOKEN;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationImplicitRequestAuthenticationConverter.TOKEN;

// A Filter that processes OpenID Provider Configuration Requests.
// See 4.1. OpenID Provider Configuration Request
public final class OidcProviderConfigurationEndpointFilter extends OncePerRequestFilter {
    // The default endpoint URI for OpenID Provider Configuration requests.
    private static final String DEFAULT_OIDC_PROVIDER_CONFIGURATION_ENDPOINT_URI = WELL_KNOWN_OPENID_CONFIGURATION;

    private final RequestMatcher requestMatcher;
    private final OidcProviderConfigurationHttpMessageConverter providerConfigurationHttpMessageConverter = new OidcProviderConfigurationHttpMessageConverter();

    public OidcProviderConfigurationEndpointFilter() {
        requestMatcher = new AntPathRequestMatcher(DEFAULT_OIDC_PROVIDER_CONFIGURATION_ENDPOINT_URI,
                HttpMethod.GET.name());
    }

    public OidcProviderConfigurationEndpointFilter(RequestMatcher requestMatcher) {
        this.requestMatcher = requestMatcher;
    }

    // scopes
    // Returns List
    private static Consumer<List<String>> scopes() {
        return strings -> {
            strings.add(OidcScopes.OPENID);
            strings.add(OidcScopes.PHONE);
            strings.add(OidcScopes.EMAIL);
            strings.add(OidcScopes.PROFILE);
        };
    }

    // Response type
    // Returns List
    private static Consumer<List<String>> responseTypes() {
        return strings -> {
            strings.add(OAuth2AuthorizationResponseType.CODE.getValue());
            strings.add(TOKEN.getValue());
            strings.add(ID_TOKEN.getValue());
        };
    }

    // Grant type
    // Returns List
    private static Consumer<List<String>> grantTypes() {
        return strings -> {
            strings.add(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
            strings.add(PASSWORD.getValue());
            strings.add(IMPLICIT.getValue());
            strings.add(AuthorizationGrantType.REFRESH_TOKEN.getValue());
        };
    }

    // Client authentication method
    // Returns List
    private static Consumer<List<String>> clientAuthenticationMethods() {
        return (authenticationMethods) -> {
            authenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue());
            authenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue());
            authenticationMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_JWT.getValue());
            authenticationMethods.add(ClientAuthenticationMethod.PRIVATE_KEY_JWT.getValue());
        };
    }

    private static String asUrl(String issuer, String endpoint) {
        return UriComponentsBuilder.fromUriString(issuer).path(endpoint).build().toUriString();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {

        if (!this.requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthorizationServerContext authorizationServerContext = AuthorizationServerContextHolder
                .getContext();
        String issuer = authorizationServerContext.getIssuer();
        AuthorizationServerSettings authorizationServerSettings = authorizationServerContext
                .getAuthorizationServerSettings();

        OidcProviderConfiguration.Builder providerConfiguration = OidcProviderConfiguration
                .builder().issuer(issuer)
                // Authentication endpoint
                .authorizationEndpoint(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getAuthorizationEndpoint()))
                // Token endpoint
                .tokenEndpoint(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getTokenEndpoint()))
                // Token endpoint authentication method
                .tokenEndpointAuthenticationMethods(clientAuthenticationMethods())
                // JWK
                .jwkSetUrl(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getJwkSetEndpoint()))
                // UserInfo endpoint
                .userInfoEndpoint(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getOidcUserInfoEndpoint()))
                // Logout endpoint
                .endSessionEndpoint(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getOidcLogoutEndpoint()))
                // Response type
                .responseTypes(responseTypes())
                // Dynamically handle grant types
                .grantTypes(grantTypes())
                // Token revocation endpoint
                .tokenRevocationEndpoint(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getTokenRevocationEndpoint()))
                // Token revocation endpoint authentication method
                .tokenRevocationEndpointAuthenticationMethods(clientAuthenticationMethods())
                // Token introspection endpoint
                .tokenIntrospectionEndpoint(asUrl(ContextService.getPortalPublicBaseUrl(),
                        authorizationServerSettings.getTokenIntrospectionEndpoint()))
                // Token introspection authentication method
                .tokenIntrospectionEndpointAuthenticationMethods(clientAuthenticationMethods())
                .subjectType("public")
                // ID_TOKEN signing endpoint
                .idTokenSigningAlgorithm(SignatureAlgorithm.RS256.getName())
                // scope
                .scopes(scopes());

        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        this.providerConfigurationHttpMessageConverter.write(providerConfiguration.build(),
                MediaType.APPLICATION_JSON, httpResponse);
    }

}
