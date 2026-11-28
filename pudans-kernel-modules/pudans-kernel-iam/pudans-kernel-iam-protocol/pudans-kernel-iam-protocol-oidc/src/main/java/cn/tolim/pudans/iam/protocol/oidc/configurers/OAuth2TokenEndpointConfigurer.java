package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.communal.constant.ProtocolConstants;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationResourceOwnerPasswordAuthenticationProvider;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2RefreshTokenAuthenticationProvider;
import cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationResourceOwnerPasswordAuthenticationConverter;
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
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.web.OAuth2TokenEndpointFilter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2DeviceCodeAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2RefreshTokenAuthenticationConverter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityConfigUtils.getPasswordEncoder;
import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityConfigUtils.getUserDetailsService;
import static cn.tolim.pudans.iam.protocol.core.configurer.AuthenticationUtils.getAuthenticationDetailsSource;
import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.OIDC_ERROR_URI;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2TokenEndpointConfigurer extends AbstractConfigurer {

    private final AuthenticationFailureHandler authenticationFailureHandler = this::sendErrorResponse;
    private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter = new OAuth2ErrorHttpMessageConverter();
    private RequestMatcher requestMatcher;

    OAuth2TokenEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    private static List<AuthenticationConverter> createDefaultAuthenticationConverters() {
        List<AuthenticationConverter> authenticationConverters = new ArrayList<>();
        // Password mode authentication converter
        authenticationConverters
                .add(new OAuth2AuthorizationResourceOwnerPasswordAuthenticationConverter());
        // Authorization code mode authentication converter
        authenticationConverters.add(new OAuth2AuthorizationCodeAuthenticationConverter());
        // Refresh token mode authentication converter
        authenticationConverters.add(new OAuth2RefreshTokenAuthenticationConverter());
        // Device mode authentication converter
        authenticationConverters.add(new OAuth2DeviceCodeAuthenticationConverter());
        return authenticationConverters;
    }

    private static List<AuthenticationProvider> createDefaultAuthenticationProviders(HttpSecurity httpSecurity) {
        List<AuthenticationProvider> authenticationProviders = new ArrayList<>();

        OAuth2AuthorizationService authorizationService = OAuth2ConfigurerUtils
                .getAuthorizationService(httpSecurity);
        OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator = OAuth2ConfigurerUtils
                .getTokenGenerator(httpSecurity);
        SessionRegistry sessionRegistry = httpSecurity.getSharedObject(SessionRegistry.class);
        UserDetailsService userDetailsService = getUserDetailsService(httpSecurity);
        PasswordEncoder passwordEncoder = getPasswordEncoder(httpSecurity);

        // Password mode authentication provider
        OAuth2AuthorizationResourceOwnerPasswordAuthenticationProvider auth2AuthorizationPasswordAuthenticationProvider = new OAuth2AuthorizationResourceOwnerPasswordAuthenticationProvider(
                userDetailsService, authorizationService, tokenGenerator, passwordEncoder);
        if (sessionRegistry != null) {
            auth2AuthorizationPasswordAuthenticationProvider.setSessionRegistry(sessionRegistry);
        }
        authenticationProviders.add(auth2AuthorizationPasswordAuthenticationProvider);

        // Authorization code authentication provider
        OAuth2AuthorizationCodeAuthenticationProvider authorizationCodeAuthenticationProvider = new OAuth2AuthorizationCodeAuthenticationProvider(
                authorizationService, tokenGenerator);
        if (sessionRegistry != null) {
            authorizationCodeAuthenticationProvider.setSessionRegistry(sessionRegistry);
        }
        authenticationProviders.add(authorizationCodeAuthenticationProvider);

        // Refresh token authentication provider
        OAuth2RefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider = new OAuth2RefreshTokenAuthenticationProvider(
                authorizationService, tokenGenerator);
        authenticationProviders.add(refreshTokenAuthenticationProvider);

        // Device mode authentication provider
        OAuth2DeviceCodeAuthenticationProvider deviceCodeAuthenticationProvider = new OAuth2DeviceCodeAuthenticationProvider(
                authorizationService, tokenGenerator);
        authenticationProviders.add(deviceCodeAuthenticationProvider);

        return authenticationProviders;
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        this.requestMatcher = new AntPathRequestMatcher(
                ProtocolConstants.OidcEndpointConstants.TOKEN_ENDPOINT, HttpMethod.POST.name());
        List<AuthenticationProvider> authenticationProviders = createDefaultAuthenticationProviders(
                httpSecurity);
        authenticationProviders.forEach(authenticationProvider -> httpSecurity
                .authenticationProvider(postProcess(authenticationProvider)));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        AuthenticationManager authenticationManager = httpSecurity
                .getSharedObject(AuthenticationManager.class);

        OAuth2TokenEndpointFilter tokenEndpointFilter = new OAuth2TokenEndpointFilter(
                authenticationManager, ProtocolConstants.OidcEndpointConstants.TOKEN_ENDPOINT);
        // Authentication detail source
        tokenEndpointFilter
                .setAuthenticationDetailsSource(getAuthenticationDetailsSource(httpSecurity));
        List<AuthenticationConverter> authenticationConverters = createDefaultAuthenticationConverters();
        tokenEndpointFilter.setAuthenticationConverter(
                new DelegatingAuthenticationConverter(authenticationConverters));
        tokenEndpointFilter.setAuthenticationFailureHandler(this.authenticationFailureHandler);
        httpSecurity.addFilterAfter(postProcess(tokenEndpointFilter), AuthorizationFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, false);
    }

    private void sendErrorResponse(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException exception) throws IOException {
        OAuth2Error error = ((OAuth2AuthenticationException) exception).getError();
        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
        OAuth2Error responseError=new OAuth2Error(error.getErrorCode(),error.getDescription(),OIDC_ERROR_URI);
        errorHttpResponseConverter.write(responseError, null, httpResponse);
    }

}
