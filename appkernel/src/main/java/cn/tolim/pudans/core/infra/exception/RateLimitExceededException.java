package cn.tolim.pudans.core.infra.exception;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RateLimitExceededException extends ResponseStatusException {

    public RateLimitExceededException(@Nullable Throwable cause) {
        super(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍候再试。", cause);
        setType(URI.create(Exceptions.REQUEST_NOT_PERMITTED_TYPE));
    }
}
