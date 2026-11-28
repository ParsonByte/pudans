package cn.tolim.pudans.iam.core.auth.otp;

import cn.tolim.pudans.contract.iam.dto.auth.OtpLoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendRequest;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendResponse;
import cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpLoginServiceTests {

    private static final AuthenticatedAccount ACCOUNT = new AuthenticatedAccount("user-1",
            "alice", "user", "org-1", "Alice");

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private OtpContextHelp otpContextHelp;

    @Test
    void sendsLoginCodeForKnownEmail() {
        OtpLoginService service = new OtpLoginService(userAccountService, otpContextHelp);
        when(userAccountService.findAccountByEmail("alice@example.com"))
                .thenReturn(Optional.of(ACCOUNT));

        try (MockedStatic<ContextService> context = Mockito.mockStatic(ContextService.class)) {
            context.when(ContextService::getCodeValidTime).thenReturn(5);
            OtpSendResponse response = service.sendCode(OtpSendRequest.builder()
                    .recipient("alice@example.com").channel("mail").build());

            assertTrue(response.getSuccess());
            assertTrue(response.getExpiresAt().isAfter(java.time.LocalDateTime.now()
                    .plusMinutes(4)));
        }
        verify(otpContextHelp).sendOtp("alice@example.com", "login", MessageNoticeChannel.MAIL);
    }

    @Test
    void hidesUnknownAccountsWhenSending() {
        OtpLoginService service = new OtpLoginService(userAccountService, otpContextHelp);
        when(userAccountService.findAccountByEmail("nobody@example.com"))
                .thenReturn(Optional.empty());

        try (MockedStatic<ContextService> context = Mockito.mockStatic(ContextService.class)) {
            context.when(ContextService::getCodeValidTime).thenReturn(5);
            OtpSendResponse response = service.sendCode(OtpSendRequest.builder()
                    .recipient("nobody@example.com").channel("mail").build());

            assertTrue(response.getSuccess());
        }
        verifyNoInteractions(otpContextHelp);
    }

    @Test
    void verifiesLoginCodeAndReturnsAccount() {
        OtpLoginService service = new OtpLoginService(userAccountService, otpContextHelp);
        when(userAccountService.findAccountByPhone("13800138000"))
                .thenReturn(Optional.of(ACCOUNT));
        when(otpContextHelp.checkOtp("login", MessageNoticeChannel.SMS, "13800138000", "123456"))
                .thenReturn(Boolean.TRUE);

        AuthenticatedAccount account = service.authenticate(OtpLoginRequest.builder()
                .recipient("+8613800138000").channel("sms").code("123456").build());

        assertEquals("user-1", account.userId());
    }

    @Test
    void rejectsInvalidLoginCode() {
        OtpLoginService service = new OtpLoginService(userAccountService, otpContextHelp);
        when(userAccountService.findAccountByEmail("alice@example.com"))
                .thenReturn(Optional.of(ACCOUNT));
        when(otpContextHelp.checkOtp("login", MessageNoticeChannel.MAIL, "alice@example.com",
                "000000")).thenReturn(Boolean.FALSE);

        assertThrows(AuthenticationFailedException.class, () -> service.authenticate(
                OtpLoginRequest.builder().recipient("alice@example.com").channel("mail")
                        .code("000000").build()));
    }
}