package cn.tolim.pudans.iam.protocol.saml.model;

import cn.tolim.pudans.iam.appkernel.core.AbstractProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_CLOCK_SKEW_SECONDS;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class SamlProtocolConfig extends AbstractProtocolConfig {

    @Serial
    private static final long serialVersionUID = 4029195482788883127L;

    // Service provider entity ID
    private String entityId;

    // Assertion consumer service URL
    private String assertionConsumerServiceUrl;

    // Service provider single logout service URL
    private String singleLogoutServiceUrl;

    // Name id format
    private SamlNameIdFormat nameIdFormat;

    // Default response binding
    private SamlBindingType defaultBinding;

    // Assertion lifetime
    private Duration assertionTimeToLive;

    // Allowed clock skew
    private Duration clockSkew;

    // Require signed assertions
    private Boolean wantAssertionsSigned;

    // Require signed authn requests
    private Boolean wantAuthnRequestsSigned;

    // Signing algorithm
    private String signingAlgorithm;

    // Signing certificate in PEM or base64 form
    private String signingCertificate;

    // Signing private key in PKCS#8 PEM form, excluded from string output
    @ToString.Exclude
    private String signingPrivateKey;

    // Attribute name to user field mappings
    private Map<String, String> attributeMappings;

    // Configured
    private Boolean configured;

    public SamlNameIdFormat getNameIdFormat() {
        return Objects.requireNonNullElse(nameIdFormat, SamlNameIdFormat.PERSISTENT);
    }

    public SamlBindingType getDefaultBinding() {
        return Objects.requireNonNullElse(defaultBinding, SamlBindingType.POST);
    }

    public Duration getAssertionTimeToLive() {
        return Objects.requireNonNullElse(assertionTimeToLive,
                Duration.ofSeconds(DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS));
    }

    public Duration getClockSkew() {
        return Objects.requireNonNullElse(clockSkew,
                Duration.ofSeconds(DEFAULT_CLOCK_SKEW_SECONDS));
    }

    public Boolean getWantAssertionsSigned() {
        return Objects.requireNonNullElse(wantAssertionsSigned, true);
    }

    public Boolean getWantAuthnRequestsSigned() {
        return Objects.requireNonNullElse(wantAuthnRequestsSigned, false);
    }

    public Map<String, String> getAttributeMappings() {
        return attributeMappings == null ? Map.of() : attributeMappings;
    }

    public Boolean getConfigured() {
        return Objects.requireNonNullElse(configured, false);
    }
}