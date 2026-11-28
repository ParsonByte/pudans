package cn.tolim.pudans.iam.authentication.webauthn.dto;

import java.util.List;

// PublicKeyCredentialRequestOptions equivalent returned to the browser
public record WebAuthnAuthenticationOptions(String challengeId, String challenge, String rpId,
                                            long timeout, String userVerification,
                                            List<WebAuthnCredentialDescriptor> allowCredentials) {

    public WebAuthnAuthenticationOptions {
        allowCredentials = allowCredentials == null ? List.of()
                : List.copyOf(allowCredentials);
    }
}