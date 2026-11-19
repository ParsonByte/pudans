package cn.tolim.pudans.core.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiSettingServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    AiSettingService service;

    @Test
    void readsTheSingletonSetting() {
        var setting = new AiSetting();
        setting.getSpec().getDefaults().setTeam("team-a");
        setting.getSpec().getDefaults().setAgent("agent-a");
        when(client.get(AiSetting.class, AiSettingService.SETTING_NAME)).thenReturn(Mono.just(setting));

        StepVerifier.create(service.defaults())
                .assertNext(defaults -> {
                    assertThat(defaults.getTeam()).isEqualTo("team-a");
                    assertThat(defaults.getAgent()).isEqualTo("agent-a");
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToAnEmptySpecWhenTheSettingHasNoSpec() {
        var setting = new AiSetting();
        setting.setSpec(null);
        when(client.get(AiSetting.class, AiSettingService.SETTING_NAME)).thenReturn(Mono.just(setting));

        StepVerifier.create(service.spec())
                .assertNext(spec -> assertThat(spec.getDefaults().getTeam()).isEqualTo("default"))
                .verifyComplete();
    }
}
