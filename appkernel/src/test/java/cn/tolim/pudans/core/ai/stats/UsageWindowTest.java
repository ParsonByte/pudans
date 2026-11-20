package cn.tolim.pudans.core.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UsageWindowTest {

    @Test
    void defaultWindowRollsOverAtBeijingMidnight() {
        var beforeMidnight = Clock.fixed(Instant.parse("2026-11-19T15:59:59Z"), ZoneOffset.UTC);
        var before = UsageWindow.of(null, null, beforeMidnight);

        assertThat(before.to()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(before.from()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(before.fromAt()).isEqualTo(Instant.parse("2026-11-12T16:00:00Z"));
        assertThat(before.toAt()).isEqualTo(Instant.parse("2026-11-19T16:00:00Z"));

        var afterMidnight = Clock.fixed(Instant.parse("2026-11-19T16:00:00Z"), ZoneOffset.UTC);
        var after = UsageWindow.of(null, null, afterMidnight);

        assertThat(after.to()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(after.from()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(after.fromAt()).isEqualTo(Instant.parse("2026-11-13T16:00:00Z"));
        assertThat(after.toAt()).isEqualTo(Instant.parse("2026-11-20T16:00:00Z"));
    }

    @Test
    void explicitRangeBecomesBeijingDayBoundaryInstants() {
        var window = UsageWindow.of(
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), Clock.systemUTC());

        assertThat(window.fromAt()).isEqualTo(Instant.parse("2026-11-19T16:00:00Z"));
        assertThat(window.toAt()).isEqualTo(Instant.parse("2026-11-20T16:00:00Z"));
    }
}