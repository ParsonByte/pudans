package cn.tolim.pudans.iam.protocol.oidc.authentication;

import cn.tolim.pudans.iam.infra.security.web.AbstractAuthenticationEntryPoint;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;

import java.io.IOException;

import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.OIDC_ERROR_URI;
import static org.springframework.security.oauth2.core.OAuth2ErrorCodes.ACCESS_DENIED;


public class ClientAuthenticationRequiredEntryPoint extends AbstractAuthenticationEntryPoint {

    private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter = new OAuth2ErrorHttpMessageConverter();

    public ClientAuthenticationRequiredEntryPoint(UserAgentParser userAgentParser) {
        super(userAgentParser);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException,
            ServletException {
        super.commence(request, response, authException);
        OAuth2Error error=new OAuth2Error(ACCESS_DENIED,authException.getMessage(),OIDC_ERROR_URI);
        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        httpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
        OAuth2Error responseError=new OAuth2Error(error.getErrorCode(),error.getDescription(),OIDC_ERROR_URI);
        errorHttpResponseConverter.write(responseError, null, httpResponse);
    }

}
