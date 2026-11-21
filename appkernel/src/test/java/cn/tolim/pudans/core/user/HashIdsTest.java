package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.core.infra.setting.ParameterGroups;
import org.junit.jupiter.api.Test;

class HashIdsTest {

    @Test
    void hashIdsChangeWhenTheSaltChanges() {
        var service = new HashIds(mock(ParameterGroups.class));
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
        var service = new HashIds(mock(ParameterGroups.class));
        service.applySalt("salt-1");

        assertThat(service.encode("alice")).isEqualTo(service.encode("alice"));
    }
}
