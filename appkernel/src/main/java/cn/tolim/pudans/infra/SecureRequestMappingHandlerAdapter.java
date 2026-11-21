package cn.tolim.pudans.infra;

import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public class SecureRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {

    @Override
    public Mono<HandlerResult> handle(ServerWebExchange exchange, Object handler) {
        return super.handle(new SecureServerWebExchange(exchange), handler);
    }
}
