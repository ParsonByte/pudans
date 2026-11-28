package cn.tolim.pudans.iam.protocol.saml.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SamlConfigData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1567394473823054819L;

    // Service provider entity ID
    private String entityId;

    // Assertion consumer service URL
    private String assertionConsumerServiceUrl;

    // Service provider single logout service URL
    private String singleLogoutServiceUrl;

    // Name id format code
    private String nameIdFormat;

    // Default response binding code
    private String defaultBinding;

    // Assertion lifetime in seconds
    private Long assertionTimeToLiveSeconds;

    // Allowed clock skew in seconds
    private Long clockSkewSeconds;

    // Require signed assertions
    private Boolean wantAssertionsSigned;

    // Require signed authn requests
    private Boolean wantAuthnRequestsSigned;

    // Signing algorithm
    private String signingAlgorithm;

    // Attribute name to user field mappings
    private Map<String, String> attributeMappings;
}