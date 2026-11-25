package cn.tolim.pudans.core.ai.endpoint;

import cn.tolim.pudans.infra.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public enum AiEndpointSupport {
    ;

    public static UUID uuid(String value, String code, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
    }
}
