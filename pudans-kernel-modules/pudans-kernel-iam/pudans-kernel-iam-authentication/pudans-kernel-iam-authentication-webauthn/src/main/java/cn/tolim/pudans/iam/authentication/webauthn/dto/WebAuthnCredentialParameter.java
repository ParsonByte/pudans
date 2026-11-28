package cn.tolim.pudans.iam.authentication.webauthn.dto;

// Public key credential parameter advertised to the browser
public record WebAuthnCredentialParameter(String type, int alg) {
}