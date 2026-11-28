package cn.tolim.pudans.iam.mfa;

import cn.tolim.pudans.iam.authentication.totp.config.TotpAuthenticationProperties;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpStatus;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpDisabledException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpNotEnrolledException;
import cn.tolim.pudans.iam.authentication.totp.repository.TotpCredentialRepository;
import cn.tolim.pudans.iam.authentication.totp.service.TotpService;
import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.entity.WebAuthnCredentialEntity;
import cn.tolim.pudans.iam.authentication.webauthn.enums.WebAuthnCredentialStatus;
import cn.tolim.pudans.iam.authentication.webauthn.repository.WebAuthnCredentialRepository;
import cn.tolim.pudans.iam.core.auth.mfa.MfaMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamMfaAuthenticatorTests {

    @Mock
    private TotpService totpService;

    @Mock
    private TotpCredentialRepository totpCredentialRepository;

    @Mock
    private WebAuthnCredentialRepository webAuthnCredentialRepository;

    private TotpAuthenticationProperties totpProperties;
    private WebAuthnProperties webAuthnProperties;
    private IamMfaAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        totpProperties = new TotpAuthenticationProperties();
        webAuthnProperties = new WebAuthnProperties();
        authenticator = new IamMfaAuthenticator(totpService, totpCredentialRepository,
                totpProperties, webAuthnCredentialRepository, webAuthnProperties);
    }

    @Test
    void listsTotpAndWebAuthnWhenBothAreEnrolled() {
        when(totpCredentialRepository.countByUserIdAndStatus("user-1", TotpStatus.ACTIVE))
                .thenReturn(1L);
        when(webAuthnCredentialRepository.findAllByUserIdAndStatusOrderByCreateTimeDesc(
                "user-1", WebAuthnCredentialStatus.ACTIVE))
                .thenReturn(List.of(new WebAuthnCredentialEntity()));

        Set<MfaMethod> methods = authenticator.availableMethods("user-1", "USER");

        assertThat(methods).containsExactly(MfaMethod.TOTP, MfaMethod.WEBAUTHN);
    }

    @Test
    void listsOnlyWebAuthnWhenTotpIsDisabled() {
        totpProperties.setEnabled(false);
        when(webAuthnCredentialRepository.findAllByUserIdAndStatusOrderByCreateTimeDesc(
                "user-1", WebAuthnCredentialStatus.ACTIVE))
                .thenReturn(List.of(new WebAuthnCredentialEntity()));

        Set<MfaMethod> methods = authenticator.availableMethods("user-1", "USER");

        assertThat(methods).containsExactly(MfaMethod.WEBAUTHN);
    }

    @Test
    void returnsEmptyWhenNoCredentialIsActive() {
        when(totpCredentialRepository.countByUserIdAndStatus("user-1", TotpStatus.ACTIVE))
                .thenReturn(0L);
        when(webAuthnCredentialRepository.findAllByUserIdAndStatusOrderByCreateTimeDesc(
                "user-1", WebAuthnCredentialStatus.ACTIVE)).thenReturn(List.of());

        assertThat(authenticator.availableMethods("user-1", "USER")).isEmpty();
    }

    @Test
    void returnsEmptyForBlankUser() {
        assertThat(authenticator.availableMethods(" ", "USER")).isEmpty();
    }

    @Test
    void verifyTotpDelegatesToTotpService() {
        when(totpService.verify("user-1", "123456")).thenReturn(true);

        assertThat(authenticator.verifyTotp("user-1", "USER", "123456")).isTrue();
    }

    @Test
    void verifyTotpReturnsFalseWhenNotEnrolled() {
        when(totpService.verify("user-1", "123456")).thenThrow(new TotpNotEnrolledException());

        assertThat(authenticator.verifyTotp("user-1", "USER", "123456")).isFalse();
    }

    @Test
    void verifyTotpReturnsFalseWhenDisabled() {
        when(totpService.verify("user-1", "123456")).thenThrow(new TotpDisabledException());

        assertThat(authenticator.verifyTotp("user-1", "USER", "123456")).isFalse();
    }
}