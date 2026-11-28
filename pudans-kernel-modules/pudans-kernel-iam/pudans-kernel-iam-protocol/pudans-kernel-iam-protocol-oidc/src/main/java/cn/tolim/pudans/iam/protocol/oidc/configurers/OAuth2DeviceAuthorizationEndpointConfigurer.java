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
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceAuthorizationRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.web.OAuth2DeviceAuthorizationEndpointFilter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2DeviceAuthorizationRequestAuthenticationConverter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.OIDC_ERROR_URI;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2DeviceAuthorizationEndpointConfigurer extends AbstractConfigurer {

    private final AuthenticationFailureHandler authenticationFailureHandler = this::sendErrorResponse;
    private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter = new OAuth2ErrorHttpMessageConverter();
    private RequestMatcher requestMatcher;

    // Restrict for internal use only.
    OAuth2DeviceAuthorizationEndpointConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        super(objectPostProcessor);
    }

    private static List<AuthenticationConverter> createDefaultAuthenticationConverters() {
        List<AuthenticationConverter> authenticationConverters = new ArrayList<>();
        authenticationConverters.add(new OAuth2DeviceAuthorizationRequestAuthenticationConverter());

        return authenticationConverters;
    }

    private static List<AuthenticationProvider> createDefaultAuthenticationProviders(HttpSecurity builder) {
        List<AuthenticationProvider> authenticationProviders = new ArrayList<>();

        OAuth2AuthorizationService authorizationService = OAuth2ConfigurerUtils
                .getAuthorizationService(builder);

        OAuth2DeviceAuthorizationRequestAuthenticationProvider deviceAuthorizationRequestAuthenticationProvider = new OAuth2DeviceAuthorizationRequestAuthenticationProvider(
                authorizationService);
        authenticationProviders.add(deviceAuthorizationRequestAuthenticationProvider);

        return authenticationProviders;
    }

    @Override
    public void init(HttpSecurity builder) {
        this.requestMatcher = new AntPathRequestMatcher(
                ProtocolConstants.OidcEndpointConstants.DEVICE_AUTHORIZATION_ENDPOINT,
                HttpMethod.POST.name());

        List<AuthenticationProvider> authenticationProviders = createDefaultAuthenticationProviders(
                builder);

        authenticationProviders.forEach(authenticationProvider -> builder
                .authenticationProvider(postProcess(authenticationProvider)));
    }

    @Override
    public void configure(HttpSecurity builder) {
        AuthenticationManager authenticationManager = builder
                .getSharedObject(AuthenticationManager.class);

        OAuth2DeviceAuthorizationEndpointFilter deviceAuthorizationEndpointFilter = new OAuth2DeviceAuthorizationEndpointFilter(
                authenticationManager,
                ProtocolConstants.OidcEndpointConstants.DEVICE_AUTHORIZATION_ENDPOINT);
        deviceAuthorizationEndpointFilter
                .setAuthenticationFailureHandler(this.authenticationFailureHandler);
        List<AuthenticationConverter> authenticationConverters = createDefaultAuthenticationConverters();
        deviceAuthorizationEndpointFilter.setAuthenticationConverter(
                new DelegatingAuthenticationConverter(authenticationConverters));
        deviceAuthorizationEndpointFilter.setVerificationUri(
                ProtocolConstants.OidcEndpointConstants.DEVICE_VERIFICATION_ENDPOINT);
        builder.addFilterAfter(postProcess(deviceAuthorizationEndpointFilter),
                AuthorizationFilter.class);
    }

    @Override
    public EndpointMatcher getEndpointMatcher() {
        return new EndpointMatcher(this.requestMatcher, true);
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
