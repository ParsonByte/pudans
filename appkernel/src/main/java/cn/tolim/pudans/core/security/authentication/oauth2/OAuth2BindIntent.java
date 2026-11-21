package cn.tolim.pudans.core.security.authentication.oauth2;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface OAuth2BindIntent {

    Mono<String> consume(ServerWebExchange exchange);

    Mono<Void> clear(ServerWebExchange exchange);
}
