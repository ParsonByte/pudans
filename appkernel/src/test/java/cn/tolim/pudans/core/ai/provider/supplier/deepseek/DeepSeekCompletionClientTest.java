package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.sse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.provider.AiCompletionRequest;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeepSeekCompletionClientTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "cmpl-1",
              "choices": [{"text": "return a + b", "index": 0, "finish_reason": "stop"}],
              "created": 1700000000,
              "model": "deepseek-flash",
              "object": "text_completion",
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 4,
                "total_tokens": 16,
                "prompt_tokens_details": {"cached_tokens": 2}
              }
            }
            """;

    private static final String CHUNK_ONE = """
            {"id":"cmpl-1","choices":[{"text":"return","index":0,"finish_reason":null}],
            "created":1700000000,"model":"deepseek-flash","object":"text_completion"}
            """;

    private static final String CHUNK_END = """
            {"id":"cmpl-1","choices":[{"text":"","index":0,"finish_reason":"stop"}],
            "created":1700000000,"model":"deepseek-flash","object":"text_completion",
            "usage":{"prompt_tokens":12,"completion_tokens":4,
            "prompt_tokens_details":{"cached_tokens":1}}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final ProviderAccounts providerAccounts = mock(ProviderAccounts.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void completesWithFimEndpoint() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var result = client(json(RESPONSE_BODY))
                .complete(provider(AiProvider.Protocol.DEEPSEEK), account(),
                        model("deepseek-flash", 128), request())
                .block();

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("cmpl-1");
        assertThat(result.model()).isEqualTo("deepseek-flash");
        assertThat(result.text()).isEqualTo("return a + b");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.usage()).isEqualTo(new AiTokenUsage(12, 4, 2, null));

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/beta/completions");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer sk-ds-secret");
        var body = body(httpRequest);
        assertThat(body)
                .contains("\"model\":\"deepseek-flash\"")
                .contains("\"prompt\":")
                .contains("\"suffix\":")
                .contains("\"max_tokens\":64")
                .contains("\"temperature\":0.2")
                .contains("\"top_p\":0.9")
                .contains("\"stop\":[\"END\"]")
                .contains("\"echo\":false")
                .contains("\"logprobs\":5")
                .contains("\"stream\":false");
    }

    @Test
    void streamsFimChunksUntilDone() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var chunks = client(sse(CHUNK_ONE, CHUNK_END, "[DONE]"))
                .stream(provider(AiProvider.Protocol.DEEPSEEK), account(),
                        model("deepseek-flash", 128), request())
                .collectList()
                .block();

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("return");
        assertThat(chunks.get(1).finishReason()).isEqualTo("stop");
        assertThat(chunks.get(1).usage()).isEqualTo(new AiTokenUsage(12, 4, 1, null));
    }

    private static AiCompletionRequest request() {
        return new AiCompletionRequest(
                "def add(a, b):\n  return", "  # end", 64, 0.2, 0.9, List.of("END"), false, 5);
    }

    private DeepSeekCompletionClient client(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new DeepSeekCompletionClient(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, providerAccounts);
    }
}
