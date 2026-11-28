package cn.tolim.pudans.iam.authentication.sms.filter;

import cn.tolim.pudans.iam.authentication.sms.constant.SmsOtpAuthenticationConstants;
import cn.tolim.pudans.iam.authentication.sms.exception.PhoneNotExistException;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.enums.SmsType;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.util.HttpResponseUtils;
import cn.tolim.pudans.iam.infra.util.PhoneUtils;
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

import static cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel.SMS;
import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;
import static cn.tolim.pudans.iam.infra.util.PhoneUtils.isPhoneValidate;


public class SendSmsOtpFilter extends OncePerRequestFilter {
    // SMS verification code login path
    public static final String LOGIN_SMS_SEND = LOGIN_PATH + "/sms/send";

    public static final RequestMatcher SMS_SEND_OPT_MATCHER = new AntPathRequestMatcher(
            LOGIN_SMS_SEND, HttpMethod.POST.name());
    private final UserRepository userRepository;
    private final OtpContextHelp otpContextHelp;

    public SendSmsOtpFilter(UserRepository userRepository, OtpContextHelp otpContextHelp) {
        this.userRepository = userRepository;
        this.otpContextHelp = otpContextHelp;
    }

    public static RequestMatcher getRequestMatcher() {
        return SMS_SEND_OPT_MATCHER;
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
        String recipient = request.getParameter(SmsOtpAuthenticationConstants.RECIPIENT_KEY);
        if (StringUtils.isBlank(recipient)) {
            throw new PhoneNotExistException();
        }
        sendOtp(response, recipient);
    }

    public void sendOtp(HttpServletResponse response, String recipient) {
        if (isPhoneValidate(recipient)) {
            // Check whether the user exists
            Optional<UserEntity> user = userRepository
                    .findByPhone(PhoneUtils.getPhoneNumber(recipient));
            if (user.isPresent()) {
                otpContextHelp.sendOtp(recipient, SmsType.LOGIN.getCode(), SMS);
                HttpResponseUtils.flushResponseJson(response, HttpStatus.OK.value(),
                        ApiRestResult.ok());
            } else {
                logger.warn("发送验证码登录失败, 手机号不存在: [{" + recipient + "}]");
                HttpResponseUtils.flushResponseJson(response, HttpStatus.OK.value(),
                        ApiRestResult.ok());
            }
        } else {
            HttpResponseUtils.flushResponseJson(response, HttpStatus.OK.value(),
                    ApiRestResult.err().message("请输入正确的手机号"));
        }
    }
}
