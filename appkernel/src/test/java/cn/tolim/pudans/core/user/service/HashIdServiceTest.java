package cn.tolim.pudans.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import org.junit.jupiter.api.Test;

class HashIdServiceTest {

    @Test
    void hashIdsChangeWhenTheSaltChanges() {
        var service = new HashIdService(mock(ParameterSettingGroupService.class));
        service.applySalt("salt-1");
        var first = service.encode("alice");

        service.applySalt("salt-2");
        var second = service.encode("alice");

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void theSameSaltIsDeterministic() {
        var service = new HashIdService(mock(ParameterSettingGroupService.class));
        service.applySalt("salt-1");

        assertThat(service.encode("alice")).isEqualTo(service.encode("alice"));
    }
}
