package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.infra.exception.Exceptions;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class TwoFactorAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    public static ServerWebExchangeMatcher MATCHER = exchange -> exchange.getPrincipal()
            .filter(TwoFactorAuthentication.class::isInstance)
            .flatMap(a -> ServerWebExchangeMatcher.MatchResult.match())
            .switchIfEmpty(ServerWebExchangeMatcher.MatchResult.notMatch());
    private final ServerResponse.Context context;

    public TwoFactorAuthenticationEntryPoint(ServerResponse.Context context) {
        this.context = context;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        var errorResponse = Exceptions.createErrorResponse(new TwoFactorAuthRequiredException(), null, exchange);
        return ServerResponse.status(errorResponse.getStatusCode())
                .bodyValue(errorResponse.getBody())
                .flatMap(response -> response.writeTo(exchange, context));
    }
}
