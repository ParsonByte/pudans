package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MemoryMaintenanceSchedulerTest {

    @Mock
    AiSettings aiSettings;

    @Mock
    AiMemoryL1Repository l1Repository;

    @Test
    void appliesDecayWhenEnabled() {
        var spec = new AiSetting.Spec();
        var memory = new AiSetting.Memory();
        memory.setDecayEnabled(true);
        memory.setDecayDailyAmount(2);
        memory.setDecayMinScore(20);
        memory.setDecayStaleDays(30);
        spec.setMemory(memory);
        when(aiSettings.spec()).thenReturn(Mono.just(spec));
        when(l1Repository.applyDecay(2, 20, 30)).thenReturn(Mono.just(5L));
        var scheduler = new MemoryMaintenanceScheduler(aiSettings, l1Repository);

        assertThat(scheduler.decayOnce().block()).isEqualTo(5L);

        verify(l1Repository).applyDecay(2, 20, 30);
    }
}