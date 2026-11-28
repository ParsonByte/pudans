package cn.tolim.pudans.iam.protocol.saml.converter;

import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;
import cn.tolim.pudans.iam.protocol.saml.pojo.AppSamlSaveConfigParam;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class AppSamlConfigConverterTests {

    private final AppSamlConfigConverter converter = new AppSamlConfigConverter() {
    };

    @Test
    void fillsDefaultsWhenOptionalValuesAreMissing() {
        AppSamlSaveConfigParam param = new AppSamlSaveConfigParam();
        param.setEntityId("https://sp.test");
        param.setAssertionConsumerServiceUrl("https://sp.test/acs");

        SamlConfigData data = converter.toConfigData(param);

        assertThat(data.getEntityId()).isEqualTo("https://sp.test");
        assertThat(data.getNameIdFormat()).isEqualTo(SamlNameIdFormat.PERSISTENT.getCode());
        assertThat(data.getDefaultBinding()).isEqualTo(SamlBindingType.POST.getCode());
        assertThat(data.getAssertionTimeToLiveSeconds()).isEqualTo(300L);
        assertThat(data.getClockSkewSeconds()).isEqualTo(60L);
        assertThat(data.getAttributeMappings()).isEmpty();
    }

    @Test
    void keepsProvidedValues() {
        AppSamlSaveConfigParam param = new AppSamlSaveConfigParam();
        param.setEntityId("https://sp.test");
        param.setAssertionConsumerServiceUrl("https://sp.test/acs");
        param.setNameIdFormat(SamlNameIdFormat.EMAIL);
        param.setDefaultBinding(SamlBindingType.REDIRECT);
        param.setAssertionTimeToLive(600);
        param.setClockSkew(30);
        param.setAttributeMappings(Map.of("mail", "email"));

        SamlConfigData data = converter.toConfigData(param);

        assertThat(data.getNameIdFormat()).isEqualTo(SamlNameIdFormat.EMAIL.getCode());
        assertThat(data.getDefaultBinding()).isEqualTo(SamlBindingType.REDIRECT.getCode());
        assertThat(data.getAssertionTimeToLiveSeconds()).isEqualTo(600L);
        assertThat(data.getClockSkewSeconds()).isEqualTo(30L);
        assertThat(data.getAttributeMappings()).containsEntry("mail", "email");
    }
}