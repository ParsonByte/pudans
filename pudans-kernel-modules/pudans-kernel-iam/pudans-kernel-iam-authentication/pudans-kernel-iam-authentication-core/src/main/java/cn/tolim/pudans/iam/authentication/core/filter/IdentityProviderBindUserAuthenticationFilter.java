package cn.tolim.pudans.iam.authentication.core.filter;

import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderBindAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderNotBindAuthentication;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.infra.util.HttpResponseUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import static cn.tolim.pudans.iam.infra.exception.enums.ExceptionStatus.EX000101;
import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;


@Slf4j
public class IdentityProviderBindUserAuthenticationFilter extends
        AbstractAuthenticationProcessingFilter {

    public final static String DEFAULT_FILTER_PROCESSES_URI = LOGIN_PATH + "/idp_bind_user";
    public static final RequestMatcher REQUEST_MATCHER = new AntPathRequestMatcher(
            DEFAULT_FILTER_PROCESSES_URI, HttpMethod.POST.name());
    public static final String USER_BIND_USERNAME_KEY = "username";
    public static final String USER_BIND_PASSWORD_KEY = "password";
    // Whether only HTTP POST requests are allowed.
    @Setter
    private boolean postOnly = true;
    private String usernameParameter = USER_BIND_USERNAME_KEY;

    private String passwordParameter = USER_BIND_PASSWORD_KEY;

    public IdentityProviderBindUserAuthenticationFilter() {
        super(REQUEST_MATCHER);
    }

    public IdentityProviderBindUserAuthenticationFilter(RequestMatcher requestMatcher) {
        super(requestMatcher);
    }

    // Attempts to bind the current unbound identity provider user.
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {
        if (this.postOnly && !"POST".equals(request.getMethod())) {
            throw new AuthenticationServiceException(
                    "不支持的身份认证方法：" + request.getMethod());
        }
        SecurityContext securityContext = SecurityUtils.getSecurityContext();
        Authentication authentication = securityContext.getAuthentication();
        if (!(authentication instanceof IdentityProviderNotBindAuthentication)) {
            return null;
        }
        String username = obtainUsername(request);
        username = (username != null) ? username.trim() : "";
        String password = obtainPassword(request);
        password = (password != null) ? password : "";
        IdentityProviderBindAuthentication identityProviderBindAuthentication = new IdentityProviderBindAuthentication(
                username, password);
        // Set authentication details from the request
        setDetails(request, identityProviderBindAuthentication);
        try {
            return this.getAuthenticationManager().authenticate(identityProviderBindAuthentication);
        }
        // Invalid username/password exception
        catch (BadCredentialsException e) {
            HttpResponseUtils.flushResponseJson(response, HttpStatus.BAD_REQUEST.value(),
                    ApiRestResult.err().message(e.getMessage()).status(EX000101.getCode()));
        }
        // Custom exception
        catch (PudansException e) {
            HttpResponseUtils.flushResponseJson(response, HttpStatus.BAD_REQUEST.value(),
                    ApiRestResult.err(e.getMessage(), e.getErrorCode()));
        }
        // Return null to prevent session loss.
        return null;
    }

    // Returns the password request parameter.
    @Nullable
    protected String obtainPassword(HttpServletRequest request) {
        return request.getParameter(this.passwordParameter);
    }

    // Returns the username request parameter.
    @Nullable
    protected String obtainUsername(HttpServletRequest request) {
        return request.getParameter(this.usernameParameter);
    }

    public final String getUsernameParameter() {
        return this.usernameParameter;
    }

    // Sets the request parameter name used to obtain the username.
    public void setUsernameParameter(String usernameParameter) {
        Assert.hasText(usernameParameter, "用户名参数不能为空");
        this.usernameParameter = usernameParameter;
    }

    public final String getPasswordParameter() {
        return this.passwordParameter;
    }

    // Sets the request parameter name used to obtain the password.
    public void setPasswordParameter(String passwordParameter) {
        Assert.hasText(passwordParameter, "密码参数不能为空");
        this.passwordParameter = passwordParameter;
    }

    // Sets authentication details from the request.
    protected void setDetails(HttpServletRequest request,
                              IdentityProviderBindAuthentication authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }
}
