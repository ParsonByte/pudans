package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Passkey assertion login request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasskeyLoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Optional username, email address or phone number
    private String username;

    // Challenge ID returned by the options endpoint
    private String challengeId;

    // WebAuthn credential ID
    private String credentialId;

    // Base64URL encoded client data JSON
    private String clientDataJson;

    // Base64URL encoded authenticator data
    private String authenticatorData;

    // Base64URL encoded assertion signature
    private String signature;
}