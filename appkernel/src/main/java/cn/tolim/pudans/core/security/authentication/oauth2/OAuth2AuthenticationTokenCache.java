package cn.tolim.pudans.core.security.authentication.oauth2;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface OAuth2AuthenticationTokenCache {


    Mono<Void> saveToken(ServerWebExchange exchange, OAuth2AuthenticationToken oauth2Token);


    Mono<OAuth2AuthenticationToken> getToken(ServerWebExchange exchange);


    Mono<Void> removeToken(ServerWebExchange exchange);
}
