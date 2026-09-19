package cn.tolim.pudans.security.authentication.oauth2;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface OAuth2BindIntent {

    Mono<Void> save(ServerWebExchange exchange, String oauth2Identity);

    Mono<String> consume(ServerWebExchange exchange);

    Mono<Void> clear(ServerWebExchange exchange);
}
