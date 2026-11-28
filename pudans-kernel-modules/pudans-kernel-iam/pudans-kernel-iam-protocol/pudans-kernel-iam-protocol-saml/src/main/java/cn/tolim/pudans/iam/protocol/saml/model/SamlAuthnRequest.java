package cn.tolim.pudans.iam.protocol.saml.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SamlAuthnRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -2887352871546351258L;

    // AuthnRequest ID
    private String id;

    // Service provider issuer
    private String issuer;

    // Assertion consumer service URL
    private String assertionConsumerServiceUrl;

    // Request destination
    private String destination;

    // Protocol binding
    private String protocolBinding;

    // Requested name id format
    private String nameIdFormat;

    // Relay state
    private String relayState;

    // Issue instant
    private String issueInstant;

    // Force authentication
    private Boolean forceAuthn;

    // Passive authentication
    private Boolean isPassive;
}