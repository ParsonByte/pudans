package cn.tolim.pudans.iam.authentication.webauthn.verification;

// Browser registration response, all binary fields are Base64URL encoded
public record WebAuthnRegistrationRequest(String challengeId, String clientDataJson,
                                          String attestationObject, String transports) {
}