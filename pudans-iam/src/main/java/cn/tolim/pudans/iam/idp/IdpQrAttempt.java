package cn.tolim.pudans.iam.idp;

import lombok.Data;

// Pending identity provider QR login attempt
@Data
public class IdpQrAttempt {

    // One-time state encoded in the QR code
    private String state;

    // Identity provider ID
    private String providerId;

    // Identity provider code
    private String providerCode;

    // Identity provider type
    private String type;

    // OAuth redirect URI
    private String redirectUri;

    // Attempt status: pending, success or failed
    private String status;

    // User resolved by the provider callback
    private String userId;

    // Failure message
    private String message;
}