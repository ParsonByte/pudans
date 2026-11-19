package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class MinimaxMessagesProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {"id":"msg-mm","type":"message","role":"assistant","model":"MiniMax-M3.1-Flash",
            "content":[{"type":"thinking","thinking":"think","signature":"sig"},
            {"type":"text","text":"Hello"}],"stop_reason":"end_turn",
            "usage":{"input_tokens":10,"output_tokens":5,"cache_read_input_tokens":2}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesMinimaxProtocolAndPostsToAnthropicPath() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.MINIMAX), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .block();

        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.MINIMAX);
        assertThat(response).isNotNull();
        assertThat(response.message().parts())
                .hasSize(2)
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                                assertThat(thinking.signature()).isEqualTo("sig")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(10, 5, 2, null));

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/anthropic/v1/messages");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer mm-key");
        assertThat(body(httpRequest)).contains("\"model\":\"MiniMax-M3.1-Flash\"");
    }

    private MinimaxMessagesProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new MinimaxMessagesProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
