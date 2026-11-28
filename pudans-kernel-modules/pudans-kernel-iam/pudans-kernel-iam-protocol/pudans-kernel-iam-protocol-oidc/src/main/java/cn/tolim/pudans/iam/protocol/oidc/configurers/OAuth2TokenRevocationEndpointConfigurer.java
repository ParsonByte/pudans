package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.communal.constant.ProtocolConstants;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import cn.tolim.pudans.iam.protocol.core.configurer.AbstractConfigurer;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.web.OAuth2TokenRevocationEndpointFilter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2TokenRevocationAuthenticationConverter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.OIDC_ERROR_URI;


@SuppressWarnings({"AlibabaClassNamingShouldBeCamel"})
public final class OAuth2TokenRevocationEndpointConfigurer extends AbstractConfigurer {
    private final AuthenticationFailureHandler authenticationFailureHandler = this::sendErrorResponse;
    private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter = new OAuth2ErrorHttpMessageConverter();
    private RequestMatcher requestMatcher;

    OAuth2TokenRevocationEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    private static List<AuthenticationConverter> createDefaultAuthenticationConverters() {
        List<AuthenticationConverter> authenticationConverters = new ArrayList<>();

        authenticationConverters.add(new OAuth2TokenRevocationAuthenticationConverter());

        return authenticationConverters;
    }

    private static List<AuthenticationProvider> createDefaultAuthenticationProviders(HttpSecurity httpSecurity) {
        List<AuthenticationProvider> authenticationProviders = new ArrayList<>();

        OAuth2TokenRevocationAuthenticationProvider tokenRevocationAuthenticationProvider = new OAuth2TokenRevocationAuthenticationProvider(
                OAuth2ConfigurerUtils.getAuthorizationService(httpSecurity));
        authenticationProviders.add(tokenRevocationAuthenticationProvider);

        return authenticationProviders;
    }

    @Override
    public void init(HttpSecurity httpSecurity) {
        this.requestMatcher = new AntPathRequestMatcher(
                ProtocolConstants.OidcEndpointConstants.TOKEN_REVOCATION_ENDPOINT,
                HttpMethod.POST.name());

        List<AuthenticationProvider> authenticationProviders = createDefaultAuthenticationProviders(
                httpSecurity);

        authenticationProviders.forEach(authenticationProvider -> httpSecurity
                .authenticationProvider(postProcess(authenticationProvider)));
    }

    @Override
    public void configure(HttpSecurity httpSecurity) {
        AuthenticationManager authenticationManager = httpSecurity
                .getSharedObject(AuthenticationManager.class);

        OAuth2TokenRevocationEndpointFilter revocationEndpointFilter = new OAuth2TokenRevocationEndpointFilter(
                authenticationManager,
                ProtocolConstants.OidcEndpointConstants.TOKEN_REVOCATION_ENDPOINT);
        List<AuthenticationConverter> authenticationConverters = createDefaultAuthenticationConverters();
        revocationEndpointFilter.setAuthenticationFailureHandler(this.authenticationFailureHandler);
        revocationEndpointFilter.setAuthenticationConverter(
                new DelegatingAuthenticationConverter(authenticationConverters));
        httpSecurity.addFilterAfter(postProcess(revocationEndpointFilter),
                AuthorizationFilter.class);
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
        this.errorHttpResponseConverter.write(responseError, null, httpResponse);
    }

}
