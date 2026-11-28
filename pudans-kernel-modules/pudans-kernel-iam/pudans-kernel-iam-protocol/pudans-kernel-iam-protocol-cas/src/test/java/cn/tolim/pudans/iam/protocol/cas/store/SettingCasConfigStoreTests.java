package cn.tolim.pudans.iam.protocol.cas.store;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.protocol.cas.model.CasConfigData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.CAS_CONFIG_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class SettingCasConfigStoreTests {

    private final SettingRepository settingRepository = mock(SettingRepository.class);
    private final SettingCasConfigStore store = new SettingCasConfigStore(settingRepository);

    @Test
    void savesNamespacedSetting() {
        when(settingRepository.findByName(CAS_CONFIG_KEY_PREFIX + "app-1")).thenReturn(null);

        store.save("app-1", CasConfigData.builder()
                .serviceUrls(new LinkedHashSet<>(java.util.Set.of("https://app.test/callback")))
                .ticketTimeToLiveSeconds(60L)
                .build());

        ArgumentCaptor<SettingEntity> captor = ArgumentCaptor.forClass(SettingEntity.class);
        verify(settingRepository).save(captor.capture());
        SettingEntity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo(CAS_CONFIG_KEY_PREFIX + "app-1");
        assertThat(saved.getValue()).contains("\"ticketTimeToLiveSeconds\":60");
    }

    @Test
    void readsConfigurationFromSettingValue() {
        SettingEntity entity = new SettingEntity();
        entity.setValue("{\"ticketTimeToLiveSeconds\":30,\"cookieName\":\"TGC\"}");
        when(settingRepository.findByName(CAS_CONFIG_KEY_PREFIX + "app-1")).thenReturn(entity);

        CasConfigData data = store.find("app-1");

        assertThat(data.getTicketTimeToLiveSeconds()).isEqualTo(30L);
        assertThat(data.getCookieName()).isEqualTo("TGC");
    }

    @Test
    void deletesByNamespacedName() {
        store.delete("app-1");

        verify(settingRepository).deleteByName(CAS_CONFIG_KEY_PREFIX + "app-1");
    }
}