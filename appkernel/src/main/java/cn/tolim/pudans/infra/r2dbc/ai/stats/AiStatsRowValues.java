package cn.tolim.pudans.infra.r2dbc.ai.stats;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;

final class AiStatsRowValues {

    private AiStatsRowValues() {}

    static long count(Row row, String column) {
        var value = row.get(column, Long.class);
        return value == null ? 0 : value;
    }

    static BigDecimal decimal(Row row, String column) {
        var value = row.get(column, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}