package cn.tolim.pudans.iam.core.auth.otp;

import cn.tolim.pudans.contract.iam.dto.auth.OtpLoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendRequest;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendResponse;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static cn.tolim.pudans.iam.core.context.ContextService.getCodeValidTime;

@Service
public class OtpLoginService {

    private final UserAccountService userAccountService;
    private final OtpContextHelp otpContextHelp;

    public OtpLoginService(UserAccountService userAccountService, OtpContextHelp otpContextHelp) {
        this.userAccountService = userAccountService;
        this.otpContextHelp = otpContextHelp;
    }

    // Sends a login verification code without revealing whether the account exists
    // Parameter request: OtpSendRequest
    // Returns: OtpSendResponse
    public OtpSendResponse sendCode(OtpSendRequest request) {
        OtpChannel channel = OtpChannel.from(request == null ? null : request.getChannel());
        String recipient = channel.normalize(request == null ? null : request.getRecipient());
        if (recipient == null) {
            throw new InvalidAuthRequestException("otp_send_request_invalid",
                    "A valid email address or phone number is required");
        }
        OtpSendResponse response = new OtpSendResponse();
        response.setSuccess(Boolean.TRUE);
        response.setExpiresAt(LocalDateTime.now().plusMinutes(getCodeValidTime()));
        try {
            findAccount(channel, recipient).ifPresent(account ->
                    otpContextHelp.sendOtp(recipient, channel.loginType(), channel.noticeChannel()));
        } catch (AuthenticationFailedException exception) {
            return response;
        }
        return response;
    }

    // Verifies a login verification code and returns the authenticated account
    // Parameter request: OtpLoginRequest
    // Returns: AuthenticatedAccount
    public AuthenticatedAccount authenticate(OtpLoginRequest request) {
        if (request == null) {
            throw new InvalidAuthRequestException("otp_login_request_invalid",
                    "The verification code request is missing");
        }
        OtpChannel channel = OtpChannel.from(request.getChannel());
        String recipient = channel.normalize(request.getRecipient());
        String code = StringUtils.trimToNull(request.getCode());
        if (recipient == null || code == null) {
            throw new InvalidAuthRequestException("otp_login_request_invalid",
                    "A valid recipient and verification code are required");
        }
        AuthenticatedAccount account = findAccount(channel, recipient)
                .orElseThrow(AuthenticationFailedException::new);
        if (!Boolean.TRUE.equals(otpContextHelp.checkOtp(channel.loginType(),
                channel.noticeChannel(), recipient, code))) {
            throw new AuthenticationFailedException("The verification code is invalid or expired");
        }
        return account;
    }

    private Optional<AuthenticatedAccount> findAccount(OtpChannel channel, String recipient) {
        return channel == OtpChannel.MAIL
                ? userAccountService.findAccountByEmail(recipient)
                : userAccountService.findAccountByPhone(recipient);
    }
}