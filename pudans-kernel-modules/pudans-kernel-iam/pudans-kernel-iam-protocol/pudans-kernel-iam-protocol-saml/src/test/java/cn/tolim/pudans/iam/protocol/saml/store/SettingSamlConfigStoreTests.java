package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.protocol.saml.model.SamlConfigData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_CONFIG_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class SettingSamlConfigStoreTests {

    private final SettingRepository settingRepository = mock(SettingRepository.class);
    private final SettingSamlConfigStore store = new SettingSamlConfigStore(settingRepository);

    @Test
    void savesNamespacedSetting() {
        when(settingRepository.findByName(SAML_CONFIG_KEY_PREFIX + "app-1")).thenReturn(null);

        store.save("app-1", SamlConfigData.builder()
                .entityId("https://sp.test")
                .assertionConsumerServiceUrl("https://sp.test/acs")
                .build());

        ArgumentCaptor<SettingEntity> captor = ArgumentCaptor.forClass(SettingEntity.class);
        verify(settingRepository).save(captor.capture());
        SettingEntity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo(SAML_CONFIG_KEY_PREFIX + "app-1");
        assertThat(saved.getValue()).contains("\"entityId\":\"https://sp.test\"");
    }

    @Test
    void readsConfigurationFromSettingValue() {
        SettingEntity entity = new SettingEntity();
        entity.setValue("{\"entityId\":\"https://sp.test\",\"nameIdFormat\":\"email\"}");
        when(settingRepository.findByName(SAML_CONFIG_KEY_PREFIX + "app-1")).thenReturn(entity);

        SamlConfigData data = store.find("app-1");

        assertThat(data.getEntityId()).isEqualTo("https://sp.test");
        assertThat(data.getNameIdFormat()).isEqualTo("email");
    }

    @Test
    void deletesByNamespacedName() {
        store.delete("app-1");

        verify(settingRepository).deleteByName(SAML_CONFIG_KEY_PREFIX + "app-1");
    }
}