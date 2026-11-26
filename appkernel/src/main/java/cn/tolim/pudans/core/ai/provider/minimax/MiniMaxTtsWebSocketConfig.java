package cn.tolim.pudans.core.ai.provider.minimax;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class MiniMaxTtsWebSocketConfig {

    // 注册升级处理器，使 WebFlux 能把握手转交给 WebSocketHandler。
    @Bean
    WebSocketHandlerAdapter miniMaxWebSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    WebSocketClient miniMaxWebSocketClient() {
        return new ReactorNettyWebSocketClient();
    }

    @Bean
    HandlerMapping miniMaxTtsWebSocketMapping(MiniMaxTtsWebSocketHandler handler) {
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of(
                MiniMaxTtsWebSocketHandler.TTS_PATTERN, handler,
                MiniMaxTtsWebSocketHandler.BIDI_PATTERN, handler));
        return mapping;
    }
}
