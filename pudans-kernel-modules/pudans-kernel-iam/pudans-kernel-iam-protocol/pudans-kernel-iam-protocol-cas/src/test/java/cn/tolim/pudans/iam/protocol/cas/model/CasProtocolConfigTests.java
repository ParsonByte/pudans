package cn.tolim.pudans.iam.protocol.cas.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_COOKIE_NAME;
import static org.assertj.core.api.Assertions.assertThat;


class CasProtocolConfigTests {

    @Test
    void exposesDefaultsForUnsetFields() {
        CasProtocolConfig config = CasProtocolConfig.builder().appCode("app-1").build();

        assertThat(config.getServiceUrls()).isEmpty();
        assertThat(config.getTicketTimeToLive()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getTicketGrantingTicketTimeToLive())
                .isEqualTo(Duration.ofSeconds(28800));
        assertThat(config.getRenewEnabled()).isFalse();
        assertThat(config.getSecureCookie()).isTrue();
        assertThat(config.getCookieName()).isEqualTo(DEFAULT_COOKIE_NAME);
        assertThat(config.getAttributeMappings()).isEmpty();
        assertThat(config.getConfigured()).isFalse();
    }
}