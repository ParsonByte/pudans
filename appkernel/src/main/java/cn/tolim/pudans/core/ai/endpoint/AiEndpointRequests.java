package cn.tolim.pudans.core.ai.endpoint;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.exception.UserNotFoundException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiEndpointRequests {

    private static final String CODE_AUTH_REQUIRED = "ai-auth-required";

    private final Accounts accounts;

    public Mono<String> userId(ServerRequest request) {
        return userId(request, CODE_AUTH_REQUIRED, "缺少有效的用户认证信息。");
    }

    public Mono<String> userId(ServerRequest request, String code, String message) {
        return request
                .principal()
                .map(Principal::getName)
                .flatMap(accounts::getUser)
                .map(user -> user.getSpec().getUid())
                .switchIfEmpty(Mono.error(authRequired(code, message)))
                .onErrorMap(UserNotFoundException.class, error -> authRequired(code, message));
    }

    public static int intParam(ServerRequest request, String name, int defaultValue, int min, int max) {
        var value = request.queryParam(name).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return defaultValue;
        }
        try {
            var parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new ServerWebInputException("查询参数 '" + name + "' 超出允许范围。");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("查询参数 '" + name + "' 必须是整数。");
        }
    }

    public static long longParam(ServerRequest request, String name, long defaultValue, long min, long max) {
        var value = request.queryParam(name).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return defaultValue;
        }
        try {
            var parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) {
                throw new ServerWebInputException("查询参数 '" + name + "' 超出允许范围。");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("查询参数 '" + name + "' 必须是整数。");
        }
    }

    private static ApiException authRequired(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
