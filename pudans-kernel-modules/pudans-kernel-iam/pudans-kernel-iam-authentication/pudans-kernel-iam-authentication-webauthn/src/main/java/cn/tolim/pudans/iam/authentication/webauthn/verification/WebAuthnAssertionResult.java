package cn.tolim.pudans.iam.authentication.webauthn.verification;

// Verified assertion result
public record WebAuthnAssertionResult(String credentialId, String userId, long signCount,
                                      boolean userVerified) {
}