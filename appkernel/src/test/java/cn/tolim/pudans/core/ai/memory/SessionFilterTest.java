package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionFilterTest {

    @Test
    void skipsInternalAndBenchmarkSessionsByDefault() {
        var filter = SessionFilter.fromSettings(new AiSetting());

        assertThat(filter.shouldSkip("internal-worker")).isTrue();
        assertThat(filter.shouldSkip("benchmark-run-1")).isTrue();
        assertThat(filter.shouldSkip("session-1")).isFalse();
    }

    @Test
    void skipsBlankAndNullSessionIds() {
        var filter = new SessionFilter(List.of("internal-"));

        assertThat(filter.shouldSkip((String) null)).isTrue();
        assertThat(filter.shouldSkip("   ")).isTrue();
    }

    @Test
    void supportsCustomPrefixesAndIgnoresBlankEntries() {
        var filter = new SessionFilter(Arrays.asList("  temp:  ", null, "", "  "));

        assertThat(filter.shouldSkip("temp:slug")).isTrue();
        assertThat(filter.shouldSkip("internal-worker")).isFalse();
    }

    @Test
    void filtersByMemoryContext() {
        var filter = new SessionFilter(List.of("internal-"));

        assertThat(filter.shouldSkip(new MemoryContext("team", "user", "agent", "internal-1", null)))
                .isTrue();
        assertThat(filter.shouldSkip(new MemoryContext("team", "user", "agent", "session-1", null)))
                .isFalse();
        assertThat(filter.shouldSkip((MemoryContext) null)).isTrue();
    }
}
