package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.infra.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CreationCursor {

    public static final int DEFAULT_LIMIT = 20;

    public static final int MAX_LIMIT = 100;

    private CreationCursor() {}

    public record Position(Instant sortTime, UUID id) {}

    public static int limit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    public static String encode(Instant sortTime, UUID id) {
        if (sortTime == null || id == null) {
            return null;
        }
        var raw = sortTime + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var separator = raw.lastIndexOf('|');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw new IllegalArgumentException("cursor is incomplete");
            }
            return new Position(
                    Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("cursor is invalid", error);
        }
    }

    public static Position decode(String cursor, String errorCode) {
        try {
            return decode(cursor);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "分页游标无效。");
        }
    }
}
