package cn.tolim.pudans.iam.core.auth.password;

import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordRequest;
import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordResponse;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetRequest;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetResponse;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.otp.OtpChannel;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
import cn.tolim.pudans.iam.infra.security.password.PasswordPolicyManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static cn.tolim.pudans.iam.core.context.ContextService.getCodeValidTime;

@Service
public class PasswordResetService {

    private final UserAccountService userAccountService;
    private final OtpContextHelp otpContextHelp;
    private final PasswordPolicyManager passwordPolicyManager;
    private final GlobalSessionService globalSessionService;

    public PasswordResetService(UserAccountService userAccountService, OtpContextHelp otpContextHelp,
                                PasswordPolicyManager passwordPolicyManager,
                                GlobalSessionService globalSessionService) {
        this.userAccountService = userAccountService;
        this.otpContextHelp = otpContextHelp;
        this.passwordPolicyManager = passwordPolicyManager;
        this.globalSessionService = globalSessionService;
    }

    // Sends a password reset code without revealing whether the account exists
    // Parameter request: ForgotPasswordRequest
    // Returns: ForgotPasswordResponse
    public ForgotPasswordResponse request(ForgotPasswordRequest request) {
        if (request == null || StringUtils.isBlank(request.getIdentifier())) {
            throw new InvalidAuthRequestException("forgot_password_request_invalid",
                    "A username, email address or phone number is required");
        }
        OtpChannel channel = OtpChannel.from(request.getChannel());
        ForgotPasswordResponse response = new ForgotPasswordResponse();
        response.setSuccess(Boolean.TRUE);
        response.setMessage("If the account exists, a verification code has been sent");
        response.setExpiresAt(LocalDateTime.now().plusMinutes(getCodeValidTime()));
        try {
            findAccount(request.getIdentifier(), channel).ifPresent(account -> {
                String recipient = recipient(account, channel);
                if (recipient != null) {
                    otpContextHelp.sendOtp(recipient, channel.passwordResetType(),
                            channel.noticeChannel());
                }
            });
        } catch (AuthenticationFailedException exception) {
            return response;
        }
        return response;
    }

    // Verifies a password reset code and replaces the account password
    // Parameter request: PasswordResetRequest
    // Returns: PasswordResetResponse
    public PasswordResetResponse reset(PasswordResetRequest request) {
        if (request == null || StringUtils.isBlank(request.getIdentifier())
                || StringUtils.isBlank(request.getToken())
                || StringUtils.isBlank(request.getNewPassword())) {
            throw new InvalidAuthRequestException("password_reset_request_invalid",
                    "The account, verification code and new password are required");
        }
        OtpChannel channel = OtpChannel.from(request.getChannel());
        AuthenticatedAccount account = findAccount(request.getIdentifier(), channel)
                .orElseThrow(() -> new AuthenticationFailedException(
                        "The verification code is invalid or expired"));
        passwordPolicyManager.validate(account.userId(), request.getNewPassword());
        String recipient = recipient(account, channel);
        if (recipient == null || !Boolean.TRUE.equals(otpContextHelp.checkOtp(
                channel.passwordResetType(), channel.noticeChannel(), recipient,
                request.getToken().trim()))) {
            throw new AuthenticationFailedException(
                    "The verification code is invalid or expired");
        }
        String encodedPassword = userAccountService.encodePassword(request.getNewPassword());
        userAccountService.updatePassword(account.userId(), account.userType(), encodedPassword);
        globalSessionService.revokeAllForUser(account.userId(), "password_reset");
        PasswordResetResponse response = new PasswordResetResponse();
        response.setSuccess(Boolean.TRUE);
        response.setMessage("The password has been reset");
        return response;
    }

    private Optional<AuthenticatedAccount> findAccount(String identifier, OtpChannel channel) {
        Optional<AuthenticatedAccount> account = channel == OtpChannel.MAIL
                ? userAccountService.findAccountByEmail(identifier)
                : userAccountService.findAccountByPhone(identifier);
        return account.isPresent()
                ? account : userAccountService.findAccountByIdentifier(identifier);
    }

    private String recipient(AuthenticatedAccount account, OtpChannel channel) {
        String value = channel == OtpChannel.MAIL
                ? userAccountService.resolveEmail(account.userId(), account.userType())
                : userAccountService.resolvePhone(account.userId(), account.userType());
        return channel.normalize(value);
    }
}