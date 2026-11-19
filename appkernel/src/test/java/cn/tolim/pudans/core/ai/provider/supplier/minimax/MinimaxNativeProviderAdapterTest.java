package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
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

class MinimaxNativeProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {"id":"cmpl","choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},
            "finish_reason":"stop"}],"model":"MiniMax-M3.1-Flash"}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesNativeProtocolAndPath() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.MINIMAX_NATIVE), account(),
                        model("MiniMax-M3.1-Flash", null), request())
                .block();

        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.MINIMAX_NATIVE);
        assertThat(response).isNotNull();
        assertThat(captured.get().url().toString())
                .isEqualTo("https://api.example.com/v1/text/chatcompletion_v2");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer mm-key");
    }

    private MinimaxNativeProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new MinimaxNativeProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
