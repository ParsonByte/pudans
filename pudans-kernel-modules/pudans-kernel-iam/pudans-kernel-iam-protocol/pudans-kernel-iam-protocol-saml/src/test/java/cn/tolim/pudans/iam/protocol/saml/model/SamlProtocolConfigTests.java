package cn.tolim.pudans.iam.protocol.saml.model;

import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class SamlProtocolConfigTests {

    @Test
    void exposesDefaultsForUnsetFields() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .build();

        assertThat(config.getNameIdFormat()).isEqualTo(SamlNameIdFormat.PERSISTENT);
        assertThat(config.getDefaultBinding()).isEqualTo(SamlBindingType.POST);
        assertThat(config.getAssertionTimeToLive()).isEqualTo(Duration.ofSeconds(300));
        assertThat(config.getClockSkew()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getWantAssertionsSigned()).isTrue();
        assertThat(config.getWantAuthnRequestsSigned()).isFalse();
        assertThat(config.getAttributeMappings()).isEmpty();
        assertThat(config.getConfigured()).isFalse();
    }

    @Test
    void keepsConfiguredValues() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .entityId("https://sp.test")
                .assertionConsumerServiceUrl("https://sp.test/acs")
                .nameIdFormat(SamlNameIdFormat.EMAIL)
                .defaultBinding(SamlBindingType.REDIRECT)
                .assertionTimeToLive(Duration.ofSeconds(600))
                .clockSkew(Duration.ofSeconds(30))
                .wantAssertionsSigned(false)
                .wantAuthnRequestsSigned(true)
                .attributeMappings(Map.of("mail", "email"))
                .configured(true)
                .build();

        assertThat(config.getEntityId()).isEqualTo("https://sp.test");
        assertThat(config.getNameIdFormat()).isEqualTo(SamlNameIdFormat.EMAIL);
        assertThat(config.getDefaultBinding()).isEqualTo(SamlBindingType.REDIRECT);
        assertThat(config.getAssertionTimeToLive()).isEqualTo(Duration.ofSeconds(600));
        assertThat(config.getWantAssertionsSigned()).isFalse();
        assertThat(config.getAttributeMappings()).containsEntry("mail", "email");
        assertThat(config.getConfigured()).isTrue();
    }
}