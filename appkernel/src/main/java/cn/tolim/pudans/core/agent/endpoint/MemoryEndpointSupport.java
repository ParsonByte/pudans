package cn.tolim.pudans.core.agent.endpoint;

import cn.tolim.pudans.infra.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

public enum MemoryEndpointSupport {
    ;

    public static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-id-invalid", "记忆标识不是有效 UUID。");
        }
    }

    public static int limit(ServerRequest request, int defaultLimit, int maxLimit) {
        var raw = request.queryParam("limit").orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultLimit;
        }
        try {
            return Math.min(Integer.parseInt(raw), maxLimit);
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("查询参数 'limit' 必须是整数。");
        }
    }
}
