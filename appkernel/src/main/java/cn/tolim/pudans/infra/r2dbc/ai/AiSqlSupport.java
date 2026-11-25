package cn.tolim.pudans.infra.r2dbc.ai;

import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

public enum AiSqlSupport {
    ;

    public static OffsetDateTime utc(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    public static <T> Mono<T> duplicateAs(
            Mono<T> source, String code, String message) {
        return source.onErrorMap(
                DuplicateKeyException.class,
                error -> new ApiException(HttpStatus.CONFLICT, code, message, error));
    }
}
