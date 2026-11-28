package cn.tolim.pudans.iam.authentication.webauthn.dto;

import java.util.List;

// PublicKeyCredentialCreationOptions equivalent returned to the browser
public record WebAuthnRegistrationOptions(String challengeId, String challenge, String rpId,
                                          String rpName, String userId, String userName,
                                          String userDisplayName,
                                          List<WebAuthnCredentialParameter> pubKeyCredParams,
                                          long timeout, String attestation,
                                          String userVerification,
                                          List<WebAuthnCredentialDescriptor> excludeCredentials) {

    public WebAuthnRegistrationOptions {
        pubKeyCredParams = pubKeyCredParams == null ? List.of()
                : List.copyOf(pubKeyCredParams);
        excludeCredentials = excludeCredentials == null ? List.of()
                : List.copyOf(excludeCredentials);
    }
}