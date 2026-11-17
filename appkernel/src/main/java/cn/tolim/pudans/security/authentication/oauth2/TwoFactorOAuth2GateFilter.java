package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

final class TwoFactorOAuth2GateFilter implements WebFilter {

    private static final String AUTHORIZATION_PATH = "/oauth2/authorization/";

    private final ServerSecurityContextRepository securityContextRepository;

    private final ServerResponse.Context context;

    TwoFactorOAuth2GateFilter(ServerSecurityContextRepository securityContextRepository, ServerResponse.Context context) {
        this.securityContextRepository = securityContextRepository;
        this.context = context;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith(AUTHORIZATION_PATH)) {
            return chain.filter(exchange);
        }
        return securityContextRepository
                .load(exchange)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext())
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(OAuth2TwoFactorSupport::requiresTwoFactor)
                .flatMap(authentication -> exchange
                        .getSession()
                        .doOnNext(session -> session.getAttributes()
                                .put(
                                        OAuth2LoginAttributes.RESUME_URI,
                                        exchange.getRequest().getURI().toString()))
                        .then(ProblemDetailResponses.write(
                                exchange,
                                context,
                                HttpStatus.UNAUTHORIZED,
                                "two-factor-required",
                                "请先完成双重验证。"))
                        .thenReturn(Boolean.TRUE))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.FALSE)))
                .then();
    }
}
