package cn.tolim.pudans.iam.core.auth.mfa;

// Multi-factor authentication methods supported by the login flow
public enum MfaMethod {

    // Time-based one-time password
    TOTP,

    // WebAuthn assertion ceremony
    WEBAUTHN
}