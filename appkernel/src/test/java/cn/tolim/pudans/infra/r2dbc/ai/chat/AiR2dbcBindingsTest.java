package cn.tolim.pudans.infra.r2dbc.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiR2dbcBindingsTest {

    @Test
    void readsPostgresJsonCodecValues() throws Exception {
        var jsonClass = Class.forName("io.r2dbc.postgresql.codec.Json");
        var value = jsonClass.getMethod("of", String.class).invoke(null, "{\"a\":1}");

        assertThat(AiR2dbcBindings.jsonText(value)).isEqualTo("{\"a\":1}");
    }
}
