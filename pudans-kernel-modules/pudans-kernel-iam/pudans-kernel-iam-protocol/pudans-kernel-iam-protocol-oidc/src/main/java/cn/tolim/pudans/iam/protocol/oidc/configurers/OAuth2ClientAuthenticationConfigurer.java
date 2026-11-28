package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.communal.constant.ProtocolConstants;
import cn.tolim.pudans.iam.infra.security.crypto.password.NoOpPasswordEncoder;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.oidc.authorization.client.OidcConfigRegisteredClientRepositoryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.PublicClientAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.web.OAuth2ClientAuthenticationFilter;
import org.springframework.security.oauth2.server.authorization.web.authentication.ClientSecretBasicAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.ClientSecretPostAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.JwtClientAssertionAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.PublicClientAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.OIDC_ERROR_URI;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2ClientAuthenticationConfigurer extends AbstractConfigurer {

    // Authentication failure handler
    private final AuthenticationFailureHandler authenticationFailureHandler = this::onAuthenticationFailure;
    // Error HTTP response converter
    private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter = new OAuth2ErrorHttpMessageConverter();
    // RequestMatcher
    private RequestMatcher requestMatcher;

    // Restrict for internal use only.
    OAuth2ClientAuthenticationConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    // Default authentication converter
    // Returns List
    private static List<AuthenticationConverter> createDefaultAuthenticationConverters() {
        List<AuthenticationConverter> authenticationConverters = new ArrayList<>();
        // JWT assertion
        authenticationConverters.add(new JwtClientAssertionAuthenticationConverter());
        // Basic authentication
        authenticationConverters.add(new ClientSecretBasicAuthenticationConverter());
        // POST authentication
        authenticationConverters.add(new ClientSecretPostAuthenticationConverter());
        // PKCE
        authenticationConverters.add(new PublicClientAuthenticationConverter());
        return authenticationConverters;
    }

    // Default identity provider
    // Parameter httpSecurity HttpSecurity
    // Returns List
    private static List<AuthenticationProvider> createDefaultAuthenticationProviders(HttpSecurity httpSecurity) {
        List<AuthenticationProvider> authenticationProviders = new ArrayList<>();

        RegisteredClientRepository registeredClientRepository = new OidcConfigRegisteredClientRepositoryWrapper(
                OAuth2ConfigurerUtils.getRegisteredClientRepository(httpSecurity));
        OAuth2AuthorizationService authorizationService = OAuth2ConfigurerUtils
                .getAuthorizationService(httpSecurity);

        // JWT assertion provider
        JwtClientAssertionAuthenticationProvider jwtClientAssertionAuthenticationProvider = new JwtClientAssertionAuthenticationProvider(
                registeredClientRepository, authorizationService);
        authenticationProviders.add(jwtClientAssertionAuthenticationProvider);

        // Client secret provider
        ClientSecretAuthenticationProvider clientSecretAuthenticationProvider = new ClientSecretAuthenticationProvider(
                registeredClientRepository, authorizationService);

        clientSecretAuthenticationProvider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());
        authenticationProviders.add(clientSecretAuthenticationProvider);

        // PKCE provider
        PublicClientAuthenticationProvider publicClientAuthenticationProvider = new PublicClientAuthenticationProvider(
                registeredClientRepository, authorizationService);
        authenticationProviders.add(publicClientAuthenticationProvider);

        return authenticationProviders;
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        this.requestMatcher = new OrRequestMatcher(
                // Token endpoint
                new AntPathRequestMatcher(ProtocolConstants.OidcEndpointConstants.TOKEN_ENDPOINT,
                        HttpMethod.POST.name()),
                // Token introspection endpoint
                new AntPathRequestMatcher(
                        ProtocolConstants.OidcEndpointConstants.TOKEN_INTROSPECTION_ENDPOINT,
                        HttpMethod.POST.name()),
                // Token revocation endpoint
                new AntPathRequestMatcher(
                        ProtocolConstants.OidcEndpointConstants.TOKEN_REVOCATION_ENDPOINT,
                        HttpMethod.POST.name()),
                // Device authorization endpoint
                new AntPathRequestMatcher(
                        ProtocolConstants.OidcEndpointConstants.DEVICE_AUTHORIZATION_ENDPOINT,
                        HttpMethod.POST.name()));

        List<AuthenticationProvider> authenticationProviders = createDefaultAuthenticationProviders(
                httpSecurity);
        authenticationProviders.forEach(authenticationProvider -> httpSecurity
                .authenticationProvider(postProcess(authenticationProvider)));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        AuthenticationManager authenticationManager = httpSecurity
                .getSharedObject(AuthenticationManager.class);
        OAuth2ClientAuthenticationFilter clientAuthenticationFilter = new OAuth2ClientAuthenticationFilter(
                authenticationManager, this.requestMatcher);
        clientAuthenticationFilter.setAuthenticationFailureHandler(authenticationFailureHandler);
        // Authentication converter
        List<AuthenticationConverter> authenticationConverters = createDefaultAuthenticationConverters();
        clientAuthenticationFilter.setAuthenticationConverter(
                new DelegatingAuthenticationConverter(authenticationConverters));
        httpSecurity.addFilterAfter(postProcess(clientAuthenticationFilter),
                AbstractPreAuthenticatedProcessingFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, false);
    }

    private void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {

        SecurityContextHolder.clearContext();

        // The authorization server MAY return an HTTP 401 (Unauthorized) status code
        // to indicate which HTTP authentication schemes are supported.
        // If the client attempted to authenticate via the "Authorization" request header field,
        // the authorization server MUST respond with an HTTP 401 (Unauthorized) status code and
        // include the "WWW-Authenticate" response header field
        // matching the authentication scheme used by the client.

        OAuth2Error error = ((OAuth2AuthenticationException) exception).getError();
        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        if (OAuth2ErrorCodes.INVALID_CLIENT.equals(error.getErrorCode())) {
            httpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
            error = new OAuth2Error(error.getErrorCode(),
                    "客户端认证失败，客户端或客户端凭证无效。",
                    OIDC_ERROR_URI);
        } else {
            httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
        }
        this.errorHttpResponseConverter.write(error, null, httpResponse);
    }

}
