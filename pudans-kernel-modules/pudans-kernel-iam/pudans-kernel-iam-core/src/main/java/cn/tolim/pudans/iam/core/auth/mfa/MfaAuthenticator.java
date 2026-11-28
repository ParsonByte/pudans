package cn.tolim.pudans.iam.core.auth.mfa;

import java.util.Set;

// SPI implemented by the runtime for TOTP and WebAuthn credential verification
public interface MfaAuthenticator {

    // Lists the verified MFA methods of an account, empty when MFA is not enrolled
    // Parameter userId: String
    // Parameter userType: String
    // Returns: Set
    Set<MfaMethod> availableMethods(String userId, String userType);

    // Verifies a TOTP code against the active credential of an account
    // Parameter userId: String
    // Parameter userType: String
    // Parameter code: String
    // Returns: boolean
    boolean verifyTotp(String userId, String userType, String code);
}