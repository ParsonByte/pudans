package cn.tolim.pudans.iam.mfa;

import cn.tolim.pudans.iam.authentication.totp.config.TotpAuthenticationProperties;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpStatus;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpDisabledException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpNotEnrolledException;
import cn.tolim.pudans.iam.authentication.totp.repository.TotpCredentialRepository;
import cn.tolim.pudans.iam.authentication.totp.service.TotpService;
import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.enums.WebAuthnCredentialStatus;
import cn.tolim.pudans.iam.authentication.webauthn.repository.WebAuthnCredentialRepository;
import cn.tolim.pudans.iam.core.auth.mfa.MfaAuthenticator;
import cn.tolim.pudans.iam.core.auth.mfa.MfaMethod;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

// MFA authenticator backed by the TOTP and WebAuthn credential modules
@Component
public class IamMfaAuthenticator implements MfaAuthenticator {

    private final TotpService totpService;
    private final TotpCredentialRepository totpCredentialRepository;
    private final TotpAuthenticationProperties totpProperties;
    private final WebAuthnCredentialRepository webAuthnCredentialRepository;
    private final WebAuthnProperties webAuthnProperties;

    public IamMfaAuthenticator(TotpService totpService,
                               TotpCredentialRepository totpCredentialRepository,
                               TotpAuthenticationProperties totpProperties,
                               WebAuthnCredentialRepository webAuthnCredentialRepository,
                               WebAuthnProperties webAuthnProperties) {
        this.totpService = totpService;
        this.totpCredentialRepository = totpCredentialRepository;
        this.totpProperties = totpProperties;
        this.webAuthnCredentialRepository = webAuthnCredentialRepository;
        this.webAuthnProperties = webAuthnProperties;
    }

    // Lists the active second factors of the account, TOTP preferred for the default UI
    // Parameter userId: String
    // Parameter userType: String
    // Returns: Set
    @Override
    public Set<MfaMethod> availableMethods(String userId, String userType) {
        Set<MfaMethod> methods = new LinkedHashSet<>();
        if (userId == null || userId.isBlank()) {
            return methods;
        }
        if (totpProperties.isEnabled()
                && totpCredentialRepository.countByUserIdAndStatus(userId, TotpStatus.ACTIVE) > 0) {
            methods.add(MfaMethod.TOTP);
        }
        if (webAuthnProperties.isEnabled() && !webAuthnCredentialRepository
                .findAllByUserIdAndStatusOrderByCreateTimeDesc(userId, WebAuthnCredentialStatus.ACTIVE)
                .isEmpty()) {
            methods.add(MfaMethod.WEBAUTHN);
        }
        return methods;
    }

    // Verifies a TOTP code through the existing TOTP service
    // Parameter userId: String
    // Parameter userType: String
    // Parameter code: String
    // Returns: boolean
    @Override
    public boolean verifyTotp(String userId, String userType, String code) {
        try {
            return totpService.verify(userId, code);
        } catch (TotpNotEnrolledException | TotpDisabledException exception) {
            return false;
        }
    }
}