package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.infra.utils.PudansUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class DefaultServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final String UNAUTHORIZED_CODE = "unauthorized";
    private static final String UNAUTHORIZED_DETAIL = "Authentication required.";
    private static final String WWW_AUTHENTICATE = "FormLogin realm=\"console\"";

    private final ServerWebExchangeMatcher xhrMatcher = exchange -> {
        if (PudansUtils.isXhr(exchange.getRequest().getHeaders())) {
            return MatchResult.match();
        }
        return MatchResult.notMatch();
    };

    private final ServerResponse.Context context;

    public DefaultServerAuthenticationEntryPoint(ServerResponse.Context context) {
        this.context = context;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return xhrMatcher
                .matches(exchange)
                .filter(MatchResult::isMatch)
                .switchIfEmpty(Mono.defer(() -> ProblemDetailResponses.write(
                                exchange,
                                context,
                                HttpStatus.UNAUTHORIZED,
                                UNAUTHORIZED_CODE,
                                UNAUTHORIZED_DETAIL)
                        .then(Mono.empty())))
                .flatMap(match -> Mono.defer(() -> {
                            var response = exchange.getResponse();
                            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE);
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return response.setComplete();
                        })
                        .then(Mono.empty()));
    }
}
