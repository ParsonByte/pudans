package cn.tolim.pudans.iam.authentication.totp.filter;

import cn.tolim.pudans.iam.authentication.core.authentication.OtpAuthentication;
import cn.tolim.pudans.iam.authentication.totp.constant.TotpAuthenticationConstants;
import cn.tolim.pudans.iam.authentication.totp.service.TotpService;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.util.HttpResponseUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import java.util.Objects;

import static cn.tolim.pudans.iam.authentication.totp.constant.TotpAuthenticationConstants.OTP_LOGIN;
import static cn.tolim.pudans.iam.infra.exception.enums.ExceptionStatus.EX000102;

// Authenticates a login request with a username and a TOTP code
public class TotpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    public static final String METHOD = "POST";
    public static final String DEFAULT_FILTER_PROCESSES_URI = OTP_LOGIN;
    public static final RequestMatcher TOTP_LOGIN_MATCHER = new AntPathRequestMatcher(
            DEFAULT_FILTER_PROCESSES_URI, HttpMethod.POST.name());

    private final Logger logger = LoggerFactory.getLogger(TotpAuthenticationFilter.class);

    private final UserDetailsService userDetailsService;
    private final TotpService totpService;
    private final RequestMatcher requestMatcher;

    private String usernameParameter = TotpAuthenticationConstants.USERNAME_PARAMETER;
    private String codeParameter = TotpAuthenticationConstants.CODE_PARAMETER;
    private boolean postOnly = true;

    public TotpAuthenticationFilter(UserDetailsService userDetailsService,
                                    TotpService totpService) {
        super(TOTP_LOGIN_MATCHER);
        Assert.notNull(userDetailsService, "userDetailsService must not be null");
        Assert.notNull(totpService, "totpService must not be null");
        this.userDetailsService = userDetailsService;
        this.totpService = totpService;
        this.requestMatcher = TOTP_LOGIN_MATCHER;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response)
            throws AuthenticationException {
        try {
            if (postOnly && !METHOD.equalsIgnoreCase(request.getMethod())) {
                throw new AuthenticationServiceException(
                        "Unsupported authentication method: " + request.getMethod());
            }
            String username = Objects.toString(obtainUsername(request), "").trim();
            String code = Objects.toString(obtainCode(request), "").trim();
            if (username.isEmpty() || code.isEmpty()) {
                flushBadRequest(response);
                return null;
            }
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            String userId = resolveUserId(userDetails, username);
            if (!totpService.verify(userId, code)) {
                logger.error("TOTP authentication failed for user [{}]", username);
                flushBadRequest(response);
                return null;
            }
            OtpAuthentication authentication = new OtpAuthentication(userDetails, username,
                    TotpAuthenticationConstants.PROVIDER_TYPE, userDetails.getAuthorities());
            setDetails(request, authentication);
            return authentication;
        } catch (Exception exception) {
            logger.error("TOTP authentication failed", exception);
            flushBadRequest(response);
            return null;
        }
    }

    // Extracts the username parameter from the request
    protected String obtainUsername(HttpServletRequest request) {
        return request.getParameter(usernameParameter);
    }

    // Extracts the TOTP code parameter from the request
    protected String obtainCode(HttpServletRequest request) {
        return request.getParameter(codeParameter);
    }

    protected void setDetails(HttpServletRequest request, OtpAuthentication authRequest) {
        authRequest.setDetails(authenticationDetailsSource.buildDetails(request));
    }

    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    public void setUsernameParameter(String usernameParameter) {
        Assert.hasText(usernameParameter, "username parameter must not be blank");
        this.usernameParameter = usernameParameter;
    }

    public void setCodeParameter(String codeParameter) {
        Assert.hasText(codeParameter, "code parameter must not be blank");
        this.codeParameter = codeParameter;
    }

    public RequestMatcher getRequestMatcher() {
        return requestMatcher;
    }

    private static String resolveUserId(UserDetails userDetails, String fallback) {
        if (userDetails instanceof cn.tolim.pudans.iam.infra.security.userdetails.UserDetails details
                && details.getId() != null && !details.getId().isBlank()) {
            return details.getId();
        }
        return fallback;
    }

    private static void flushBadRequest(HttpServletResponse response) {
        HttpResponseUtils.flushResponseJson(response, HttpStatus.BAD_REQUEST.value(),
                ApiRestResult.builder().status(EX000102.getCode())
                        .message(EX000102.getMessage()).build());
    }
}