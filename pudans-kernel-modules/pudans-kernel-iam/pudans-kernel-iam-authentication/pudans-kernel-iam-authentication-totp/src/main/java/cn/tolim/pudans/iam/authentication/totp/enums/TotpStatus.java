package cn.tolim.pudans.iam.authentication.totp.enums;

// Lifecycle status of a TOTP credential
public enum TotpStatus {
    // Secret generated but not yet confirmed by the user
    PENDING,

    // Secret confirmed and usable for authentication
    ACTIVE,

    // Credential disabled by the user or an administrator
    DISABLED
}