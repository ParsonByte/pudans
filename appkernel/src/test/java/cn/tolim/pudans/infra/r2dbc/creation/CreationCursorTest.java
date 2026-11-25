package cn.tolim.pudans.infra.r2dbc.creation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreationCursorTest {

    @Test
    void roundTripsTheSortTimeAndIdentifier() {
        var time = Instant.parse("2026-11-25T08:00:00Z");
        var id = UUID.randomUUID();

        var decoded = CreationCursor.decode(CreationCursor.encode(time, id));

        assertThat(decoded).isNotNull();
        assertThat(decoded.sortTime()).isEqualTo(time);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void rejectsMalformedCursors() {
        assertThatThrownBy(() -> CreationCursor.decode("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampsThePageSizeToTheConfiguredMaximum() {
        assertThat(CreationCursor.limit(null)).isEqualTo(CreationCursor.DEFAULT_LIMIT);
        assertThat(CreationCursor.limit(0)).isEqualTo(CreationCursor.DEFAULT_LIMIT);
        assertThat(CreationCursor.limit(5)).isEqualTo(5);
        assertThat(CreationCursor.limit(1000)).isEqualTo(CreationCursor.MAX_LIMIT);
    }
}
