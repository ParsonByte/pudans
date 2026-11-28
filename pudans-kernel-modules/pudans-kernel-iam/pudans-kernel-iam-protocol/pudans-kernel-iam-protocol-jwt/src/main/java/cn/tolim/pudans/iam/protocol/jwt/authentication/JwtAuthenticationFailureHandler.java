
package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.protocol.jwt.endpoint.http.converter.JwtErrorHttpMessageConverter;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

import static cn.tolim.pudans.iam.infra.context.ServletContextService.isHtmlRequest;


public class JwtAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final HttpMessageConverter<JwtError> errorHttpResponseConverter = new JwtErrorHttpMessageConverter();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException,
            ServletException {
        if (exception instanceof JwtAuthenticationException) {
            ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
            JwtError error = ((JwtAuthenticationException) exception).getError();
            boolean isHtmlRequest = isHtmlRequest(request);
            // JSON
            if (!isHtmlRequest) {
                if (error.getErrorCode().equals(JwtErrorCodes.SERVER_ERROR)) {
                    httpResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                }
                if (error.getErrorCode().equals(JwtErrorCodes.INVALID_REQUEST)) {
                    httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
                }
                errorHttpResponseConverter.write(error, null, httpResponse);
                return;
            }
            // Html
            response.sendError(HttpStatus.BAD_REQUEST.value(), error.toString());
        }
    }

}
