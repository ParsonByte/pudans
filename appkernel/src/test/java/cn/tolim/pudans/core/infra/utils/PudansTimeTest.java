package cn.tolim.pudans.core.infra.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class PudansTimeTest {

    @Test
    void usesBeijingAsTheSingleBusinessTimeZone() {
        assertThat(PudansTime.BEIJING).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(PudansTime.systemClock().getZone()).isEqualTo(PudansTime.BEIJING);
    }

    @Test
    void beijingMidnightStartsAtThePreviousUtcDaySixteen() {
        var beforeMidnight = Instant.parse("2026-11-19T15:59:59Z");
        var beijingMidnight = Instant.parse("2026-11-19T16:00:00Z");

        assertThat(PudansTime.dateOf(beforeMidnight)).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(PudansTime.dateOf(beijingMidnight)).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(PudansTime.startOfDay(beijingMidnight)).isEqualTo(beijingMidnight);
        assertThat(PudansTime.startOfDay(LocalDate.of(2026, 11, 20))).isEqualTo(beijingMidnight);
    }

    @Test
    void dayBoundariesDoNotDependOnTheJvmDefaultTimeZone() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));

            assertThat(PudansTime.dateOf(Instant.parse("2026-11-19T16:00:00Z")))
                    .isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(PudansTime.startOfDay(Instant.parse("2026-11-19T16:00:00Z")))
                    .isEqualTo(Instant.parse("2026-11-19T16:00:00Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void appliesBeijingAsTheJvmDefaultTimeZone() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            PudansTime.applySystemTimeZone();

            assertThat(TimeZone.getDefault().getID()).isEqualTo(PudansTime.BEIJING.getId());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}