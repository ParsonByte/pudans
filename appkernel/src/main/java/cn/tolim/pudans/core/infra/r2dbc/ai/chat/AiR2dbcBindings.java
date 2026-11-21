package cn.tolim.pudans.core.infra.r2dbc.ai.chat;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;

public final class AiR2dbcBindings {

    private AiR2dbcBindings() {}

    public static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, @Nullable Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    public static @Nullable String jsonText(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        try {
            var asString = value.getClass().getMethod("asString");
            asString.setAccessible(true);
            return (String) asString.invoke(value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // 其他驱动可能直接返回字符串。
        }
        return value.toString();
    }
}
