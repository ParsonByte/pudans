package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ProblemDetailAccessDeniedHandler implements ServerAccessDeniedHandler {

    private static final String ACCESS_DENIED_CODE = "access-denied";
    private static final String ACCESS_DENIED_DETAIL = "访问被拒绝。";

    private final ServerResponse.Context context;

    public ProblemDetailAccessDeniedHandler(ServerResponse.Context context) {
        this.context = context;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return ProblemDetailResponses.write(
                exchange, context, HttpStatus.FORBIDDEN, ACCESS_DENIED_CODE, ACCESS_DENIED_DETAIL);
    }
}
