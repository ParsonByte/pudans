package cn.tolim.pudans.iam.authentication.mail.filter;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import cn.tolim.pudans.iam.authentication.core.authentication.OtpAuthentication;
import cn.tolim.pudans.iam.authentication.mail.constant.MailOtpAuthenticationConstants;
import cn.tolim.pudans.iam.authentication.mail.exception.CaptchaNotExistException;
import cn.tolim.pudans.iam.authentication.mail.exception.MailNotExistException;
import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
import cn.tolim.pudans.iam.infra.exception.InfoValidityFailException;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.util.HttpResponseUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
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

import static cn.tolim.pudans.iam.authentication.mail.constant.MailOtpAuthenticationConstants.OTP_LOGIN;
import static cn.tolim.pudans.iam.infra.exception.enums.ExceptionStatus.EX000102;


public class MailOtpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    // Request method
    public static final String METHOD = "POST";
    public final static String DEFAULT_FILTER_PROCESSES_URI = OTP_LOGIN + "/mail";
    public static final RequestMatcher MAIL_LOGIN_MATCHER = new AntPathRequestMatcher(
            DEFAULT_FILTER_PROCESSES_URI, HttpMethod.POST.name());
    private final Logger logger = LoggerFactory
            .getLogger(MailOtpAuthenticationFilter.class);
    private final OtpContextHelp otpContextHelp;
    private final UserDetailsService userDetailsService;
    private String recipientParameter = MailOtpAuthenticationConstants.RECIPIENT_KEY;
    private String codeParameter = MailOtpAuthenticationConstants.CODE_KEY;
    // Whether to process POST requests
    private boolean postOnly = true;

    public MailOtpAuthenticationFilter(UserDetailsService userDetailsService,
                                       OtpContextHelp otpContextHelp) {
        super(MAIL_LOGIN_MATCHER);
        this.userDetailsService = userDetailsService;
        this.otpContextHelp = otpContextHelp;
    }

    public static RequestMatcher getRequestMatcher() {
        return MAIL_LOGIN_MATCHER;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException {
        try {
            if (postOnly && !METHOD.equalsIgnoreCase(request.getMethod())) {
                throw new AuthenticationServiceException(
                        "不支持的身份认证方法：" + request.getMethod());
            }
            // Get phone number/email
            String recipient = Objects.toString(obtainUsername(request), "").trim();
            if (StringUtils.isBlank(recipient)) {
                throw new MailNotExistException();
            }
            String code = Objects.toString(obtainCode(request), "").trim();
            if (StringUtils.isBlank(code)) {
                throw new CaptchaNotExistException();
            }
            UserDetails userDetails = userDetailsService.loadUserByUsername(recipient);
            IdentityProviderType type = checkOtp(recipient, code);
            OtpAuthentication authentication = new OtpAuthentication(userDetails, recipient,
                    type.value(), userDetails.getAuthorities());
            // Allow subclasses to set the "details" property
            setDetails(request, authentication);
            return authentication;
        } catch (Exception e) {
            HttpResponseUtils.flushResponseJson(response, HttpStatus.BAD_REQUEST.value(),
                    ApiRestResult.builder().status(EX000102.getCode()).message(EX000102.getMessage())
                            .build());
            return null;
        }
    }

    public IdentityProviderType checkOtp(String recipient, String code) {
        Boolean checkOtp = otpContextHelp.checkOtp(MailType.LOGIN.getCode(),
                MessageNoticeChannel.MAIL, recipient, code);
        if (!checkOtp) {
            logger.error("用户邮箱: [{}], 验证码: [{}] 认证失败", recipient, code);
            throw new InfoValidityFailException(EX000102.getMessage());
        }
        return IdentityProviderType.MAIL;
    }

    public String getFilterProcessesUri() {
        return DEFAULT_FILTER_PROCESSES_URI;
    }

    // Enables subclasses to override the composition of the username, such as
    // by including additional values and a separator.
    protected String obtainUsername(HttpServletRequest request) {
        return request.getParameter(recipientParameter);
    }

    protected String obtainCode(HttpServletRequest request) {
        return request.getParameter(codeParameter);
    }

    // Provided so that subclasses may configure what is put into the
    // authentication request's details property.
    protected void setDetails(HttpServletRequest request, OtpAuthentication authRequest) {
        authRequest.setDetails(authenticationDetailsSource.buildDetails(request));
    }

    // Defines whether only HTTP POST requests will be allowed by this filter.
    // If set to true, and an authentication request is received which is not a
    // POST request, an exception will be raised immediately and authentication
    // will not be attempted. The unsuccessfulAuthentication() method
    // will be called as if handling a failed authentication.
    // Defaults to true but may be overridden by subclasses.
    public void setPostOnly(boolean postOnly) {
        this.postOnly = postOnly;
    }

    public final String getRecipientParameter() {
        return recipientParameter;
    }

    public void setRecipientParameter(String codeParameter) {
        Assert.hasText(recipientParameter, "邮箱参数不能为空");
        this.recipientParameter = codeParameter;
    }

    public final String getCodeParameter() {
        return codeParameter;
    }

    public void setCodeParameter(String codeParameter) {
        Assert.hasText(codeParameter, "验证码参数不能为空");
        this.codeParameter = codeParameter;
    }
}
