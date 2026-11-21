package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.properties.PudansProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ParameterSettingExternalUrlSupplierTest {

    @Mock
    private ParameterSettingFetcher systemConfigFetcher;

    @Mock
    private PudansProperties pudansProperties;

    @Mock
    private WebFluxProperties webFluxProperties;

    @Test
    void refetchReturnsEmptyForFreshSettings() {
        when(systemConfigFetcher.fetch(ParameterSetting.Site.GROUP, ParameterSetting.Site.class))
                .thenReturn(Mono.empty());
        when(systemConfigFetcher.getBasic()).thenReturn(Mono.just(new ParameterSetting.Basic()));

        var supplier = new ParameterSettingExternalUrlSupplier(
                pudansProperties, webFluxProperties, systemConfigFetcher);

        assertThat(supplier.refetchExternalUrl()).isEmpty();
    }
}
