package cn.tolim.pudans.iam.protocol.core;

import java.io.Serializable;
import java.time.Instant;


public interface RegisteredClient extends Serializable {
    // Application ID
    String getId();

    // Application code
    String getCode();

    // Client ID
    String getClientId();

    // Client name
    String getClientName();

    // Client ID creation time
    Instant getClientIdIssuedAt();

    // Client secret
    String getClientSecret();

    // Client secret creation time
    Instant getClientSecretExpiresAt();
}
