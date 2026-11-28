package cn.tolim.pudans.iam.authentication.webauthn.verification;

// Browser assertion response, all binary fields are Base64URL encoded
public record WebAuthnAssertionRequest(String challengeId, String credentialId,
                                       String clientDataJson, String authenticatorData,
                                       String signature) {
}