package cn.tolim.pudans.iam.authentication.mail.filter;

import cn.tolim.pudans.iam.authentication.mail.constant.MailOtpAuthenticationConstants;
import cn.tolim.pudans.iam.authentication.mail.exception.MailNotExistException;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.util.HttpResponseUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

import static cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel.MAIL;
import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;


public class SendMailOtpFilter extends OncePerRequestFilter {

    // Email verification code login path
    public static final String LOGIN_MAIL_SEND = LOGIN_PATH + "/mail/send";

    public static final RequestMatcher MAIL_SEND_OPT_MATCHER = new AntPathRequestMatcher(
            LOGIN_MAIL_SEND, HttpMethod.POST.name());
    private final UserRepository userRepository;
    private final OtpContextHelp otpContextHelp;

    public SendMailOtpFilter(UserRepository userRepository, OtpContextHelp otpContextHelp) {
        this.userRepository = userRepository;
        this.otpContextHelp = otpContextHelp;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException,
            IOException {
        if (!getRequestMatcher().matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String recipient = request.getParameter(MailOtpAuthenticationConstants.RECIPIENT_KEY);
        if (StringUtils.isBlank(recipient)) {
            throw new MailNotExistException();
        }
        boolean isSend = sendOtp(recipient);
        // Send OTP
        if (isSend) {
            HttpResponseUtils.flushResponseJson(response, HttpStatus.OK.value(),
                    ApiRestResult.ok());
            return;
        }
        HttpResponseUtils.flushResponseJson(response, HttpStatus.OK.value(),
                ApiRestResult.err().message("请输入正确的邮箱"));
    }

    public RequestMatcher getRequestMatcher() {
        return MAIL_SEND_OPT_MATCHER;
    }

    public boolean sendOtp(String recipient) {
        // Check whether the user exists
        Optional<UserEntity> user = userRepository.findByEmail(recipient);
        if (user.isPresent()) {
            otpContextHelp.sendOtp(recipient, MailType.LOGIN.getCode(), MAIL);
            return true;
        }
        return false;
    }
}
