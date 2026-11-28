package cn.tolim.pudans.iam.protocol.cas.converter;

import cn.tolim.pudans.iam.protocol.cas.model.CasConfigData;
import cn.tolim.pudans.iam.protocol.cas.pojo.AppCasSaveConfigParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_COOKIE_NAME;
import static org.assertj.core.api.Assertions.assertThat;


class AppCasConfigConverterTests {

    private final AppCasConfigConverter converter = new AppCasConfigConverter() {
    };

    @Test
    void fillsDefaultsWhenOptionalValuesAreMissing() {
        AppCasSaveConfigParam param = new AppCasSaveConfigParam();
        param.setServiceUrls(List.of("https://app.test/callback"));

        CasConfigData data = converter.toConfigData(param);

        assertThat(data.getServiceUrls()).containsExactly("https://app.test/callback");
        assertThat(data.getTicketTimeToLiveSeconds()).isEqualTo(60L);
        assertThat(data.getTicketGrantingTicketTimeToLiveSeconds()).isEqualTo(28800L);
        assertThat(data.getCookieName()).isEqualTo(DEFAULT_COOKIE_NAME);
        assertThat(data.getAttributeMappings()).isEmpty();
    }

    @Test
    void keepsProvidedValues() {
        AppCasSaveConfigParam param = new AppCasSaveConfigParam();
        param.setServiceUrls(List.of("https://app.test/callback"));
        param.setTicketTimeToLive(30);
        param.setTicketGrantingTicketTimeToLive(3600);
        param.setCookieName("CUSTOM_TGC");
        param.setRenewEnabled(true);
        param.setSecureCookie(false);

        CasConfigData data = converter.toConfigData(param);

        assertThat(data.getTicketTimeToLiveSeconds()).isEqualTo(30L);
        assertThat(data.getTicketGrantingTicketTimeToLiveSeconds()).isEqualTo(3600L);
        assertThat(data.getCookieName()).isEqualTo("CUSTOM_TGC");
        assertThat(data.getRenewEnabled()).isTrue();
        assertThat(data.getSecureCookie()).isFalse();
    }
}