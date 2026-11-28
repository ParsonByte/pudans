package cn.tolim.pudans.iam.authentication.webauthn.enums;

// Lifecycle status of a WebAuthn credential
public enum WebAuthnCredentialStatus {
    // Usable for authentication
    ACTIVE,

    // Disabled by the user or an administrator
    DISABLED
}