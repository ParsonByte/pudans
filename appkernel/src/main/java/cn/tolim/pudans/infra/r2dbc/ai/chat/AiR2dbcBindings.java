package cn.tolim.pudans.infra.r2dbc.ai.chat;

import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;

public final class AiR2dbcBindings {

    private AiR2dbcBindings() {}

    public static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, @Nullable Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
