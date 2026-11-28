package cn.tolim.pudans.iam.authentication.webauthn.dto;

import java.util.List;

// Credential descriptor used by allow and exclude lists
public record WebAuthnCredentialDescriptor(String id, String type, List<String> transports) {

    public WebAuthnCredentialDescriptor {
        transports = transports == null ? List.of() : List.copyOf(transports);
    }
}