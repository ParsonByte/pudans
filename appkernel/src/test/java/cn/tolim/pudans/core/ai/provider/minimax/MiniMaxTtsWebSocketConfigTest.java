package cn.tolim.pudans.core.ai.provider.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.HandlerMapping;

class MiniMaxTtsWebSocketConfigTest {

    @Test
    void mapsBothTtsSocketPathsToTheHandler() {
        var handler = mock(MiniMaxTtsWebSocketHandler.class);

        new ApplicationContextRunner()
                .withBean(MiniMaxTtsWebSocketHandler.class, () -> handler)
                .withUserConfiguration(MiniMaxTtsWebSocketConfig.class)
                .run(context -> {
                    var mapping = context.getBean(HandlerMapping.class);
                    for (var operation : List.of("tts-websocket", "tts-websocket-bidi")) {
                        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                                "/apis/api.pudans.tolim.cn/v1alpha1/aichannels/"
                                        + UUID.randomUUID()
                                        + "/-/minimax/"
                                        + operation));
                        assertThat(mapping.getHandler(exchange).block()).isSameAs(handler);
                    }
                });
    }
}
