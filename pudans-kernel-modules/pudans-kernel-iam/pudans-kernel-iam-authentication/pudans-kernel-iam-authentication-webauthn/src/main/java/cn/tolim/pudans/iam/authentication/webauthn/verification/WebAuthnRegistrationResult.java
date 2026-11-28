package cn.tolim.pudans.iam.authentication.webauthn.verification;

import java.util.List;

// Verified registration data ready to be persisted
public record WebAuthnRegistrationResult(String userId, String username, String credentialId,
                                         String publicKeyCose, int algorithm, long signCount,
                                         String aaguid, String attestationFormat,
                                         boolean backupEligible, boolean backupState,
                                         List<String> transports) {

    public WebAuthnRegistrationResult {
        transports = transports == null ? List.of() : List.copyOf(transports);
    }
}