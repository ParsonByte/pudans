package cn.tolim.pudans.core.infra.r2dbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class R2dbcConfigurationTest {

    @Test
    void setsTheBeijingSessionTimeZoneOnEveryConnection() {
        var builder = ConnectionFactoryOptions.builder();

        R2dbcConfiguration.beijingSessionTimeZone().customize(builder);

        var options = builder.build();
        var timeZone = (TimeZone) options.getRequiredValue(R2dbcConfiguration.POSTGRES_TIME_ZONE);
        assertThat(timeZone.getID()).isEqualTo("Asia/Shanghai");
    }
}