package cn.tolim.pudans.iam.authentication.webauthn.dto;

// Number of WebAuthn credentials changed by a disable request
public record WebAuthnDisableResponse(int disabledCount) {
}