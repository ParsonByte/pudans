package cn.tolim.pudans.iam.core.auth.password;

import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordRequest;
import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordResponse;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetRequest;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetResponse;
import cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
import cn.tolim.pudans.iam.infra.security.password.PasswordPolicyManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {

    private static final AuthenticatedAccount ACCOUNT = new AuthenticatedAccount("user-1",
            "alice", "user", "org-1", "Alice");

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private OtpContextHelp otpContextHelp;

    @Mock
    private PasswordPolicyManager passwordPolicyManager;

    @Mock
    private GlobalSessionService globalSessionService;

    @Test
    void sendsResetCodeForKnownEmail() {
        PasswordResetService service = service();
        when(userAccountService.findAccountByEmail("alice"))
                .thenReturn(Optional.of(ACCOUNT));
        when(userAccountService.resolveEmail("user-1", "user"))
                .thenReturn("alice@example.com");

        try (MockedStatic<ContextService> context = Mockito.mockStatic(ContextService.class)) {
            context.when(ContextService::getCodeValidTime).thenReturn(10);
            ForgotPasswordResponse response = service.request(ForgotPasswordRequest.builder()
                    .identifier("alice").channel("mail").build());

            assertTrue(response.getSuccess());
        }
        verify(otpContextHelp).sendOtp("alice@example.com", "forget_password",
                MessageNoticeChannel.MAIL);
    }

    @Test
    void resetsPasswordAndRevokesSessions() {
        PasswordResetService service = service();
        when(userAccountService.findAccountByEmail("alice@example.com"))
                .thenReturn(Optional.of(ACCOUNT));
        when(userAccountService.resolveEmail("user-1", "user"))
                .thenReturn("alice@example.com");
        when(otpContextHelp.checkOtp("forget_password", MessageNoticeChannel.MAIL,
                "alice@example.com", "654321")).thenReturn(Boolean.TRUE);
        when(userAccountService.encodePassword("new-secret")).thenReturn("encoded");

        PasswordResetResponse response = service.reset(PasswordResetRequest.builder()
                .identifier("alice@example.com").channel("mail").token("654321")
                .newPassword("new-secret").build());

        assertTrue(response.getSuccess());
        verify(passwordPolicyManager).validate("user-1", "new-secret");
        verify(userAccountService).updatePassword("user-1", "user", "encoded");
        verify(globalSessionService).revokeAllForUser("user-1", "password_reset");
    }

    @Test
    void rejectsInvalidResetCode() {
        PasswordResetService service = service();
        when(userAccountService.findAccountByEmail("alice@example.com"))
                .thenReturn(Optional.of(ACCOUNT));
        when(userAccountService.resolveEmail("user-1", "user"))
                .thenReturn("alice@example.com");
        when(otpContextHelp.checkOtp("forget_password", MessageNoticeChannel.MAIL,
                "alice@example.com", "000000")).thenReturn(Boolean.FALSE);

        assertThrows(AuthenticationFailedException.class, () -> service.reset(
                PasswordResetRequest.builder().identifier("alice@example.com").channel("mail")
                        .token("000000").newPassword("new-secret").build()));
    }

    @Test
    void hidesUnknownAccountsWhenSending() {
        PasswordResetService service = service();
        when(userAccountService.findAccountByPhone("nobody")).thenReturn(Optional.empty());

        try (MockedStatic<ContextService> context = Mockito.mockStatic(ContextService.class)) {
            context.when(ContextService::getCodeValidTime).thenReturn(10);
            ForgotPasswordResponse response = service.request(ForgotPasswordRequest.builder()
                    .identifier("nobody").channel("sms").build());

            assertTrue(response.getSuccess());
            assertEquals("If the account exists, a verification code has been sent",
                    response.getMessage());
        }
    }

    private PasswordResetService service() {
        return new PasswordResetService(userAccountService, otpContextHelp,
                passwordPolicyManager, globalSessionService);
    }
}