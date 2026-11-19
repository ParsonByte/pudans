package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.sse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekChatProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekResponsesProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxChatProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxMessagesProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxNativeProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxResponsesProviderAdapter;
import cn.tolim.pudans.core.ai.provider.supplier.qianwen.QianwenChatProviderAdapter;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class CustomHttpProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "chatcmpl-1",
              "model": "custom-model",
              "choices": [
                {"index": 0, "message": {"role": "assistant", "content": "Hello"},
                 "finish_reason": "stop"}
              ],
              "usage": {"prompt_tokens": 3, "completion_tokens": 2}
            }
            """;

    private static final String CONTENT_CHUNK = """
            {"choices":[{"index":0,"delta":{"content":"Hel"}}]}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.CUSTOM_HTTP);
    }

    @Test
    void postsOpenAiCompatibleChat() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var provider = provider(AiProvider.Protocol.CUSTOM_HTTP);
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider, account(), model("custom-model", null), request())
                .block();

        assertThat(response).isNotNull();
        assertThat(response.message().parts())
                .filteredOn(AiContentPart.Text.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                        assertThat(text.text()).isEqualTo("Hello"));

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString()).isEqualTo("https://api.example.com/v1/chat/completions");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        assertThat(httpRequest.headers().getFirst("X-Org-Id")).isEqualTo("org-1");
        var body = body(httpRequest);
        assertThat(body).contains("\"model\":\"custom-model\"");
        assertThat(body).contains("\"stream\":false");
    }

    @Test
    void streamsSseUntilDone() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        var events = adapter(sse(CONTENT_CHUNK, "[DONE]"))
                .streamChat(provider(AiProvider.Protocol.CUSTOM_HTTP), account(),
                        model("custom-model", null), request())
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).content()).isEqualTo("Hel");

        var httpRequest = captured.get();
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    @Test
    void rejectsMissingBaseUrlWithConfigCode() {
        var provider = provider(AiProvider.Protocol.CUSTOM_HTTP);
        provider.getSpec().setBaseUrl(null);

        assertThatThrownBy(() -> adapter(json("{}"))
                        .chat(provider, account(), model("custom-model", null), request())
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(AiProviderConfigSupport.CODE_INVALID);
                });
    }

    @Test
    void rejectsBaseUrlWithFragment() {
        var provider = provider(AiProvider.Protocol.CUSTOM_HTTP);
        provider.getSpec().setBaseUrl("https://api.example.com/#fragment");

        assertThatThrownBy(() -> adapter(json("{}"))
                        .streamChat(provider, account(), model("custom-model", null), request())
                        .blockLast())
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiProviderConfigSupport.CODE_INVALID));
    }

    @Test
    void registryDiscoversAllProtocols() {
        var webClient = WebClient.builder().build();
        var registry = new AiProviderAdapterRegistry(List.<AiProviderAdapter>of(
                new OpenAiChatProviderAdapter(webClient, mapper, accountService),
                new AnthropicMessagesProviderAdapter(webClient, mapper, accountService),
                new OpenAiResponsesProviderAdapter(webClient, mapper, accountService),
                new GeminiProviderAdapter(webClient, mapper, accountService),
                new GeminiOpenAiProviderAdapter(webClient, mapper, accountService),
                new OllamaProviderAdapter(webClient, mapper, accountService),
                new DeepSeekProviderAdapter(webClient, mapper, accountService),
                new DeepSeekChatProviderAdapter(webClient, mapper, accountService),
                new DeepSeekResponsesProviderAdapter(webClient, mapper, accountService),
                new MinimaxMessagesProviderAdapter(webClient, mapper, accountService),
                new MinimaxChatProviderAdapter(webClient, mapper, accountService),
                new MinimaxNativeProviderAdapter(webClient, mapper, accountService),
                new MinimaxResponsesProviderAdapter(webClient, mapper, accountService),
                new QianwenChatProviderAdapter(webClient, mapper, accountService),
                new CustomHttpProviderAdapter(webClient, mapper, accountService)));

        for (var protocol : AiProvider.Protocol.values()) {
            assertThat(registry.require(protocol).protocol()).isEqualTo(protocol);
        }
    }

    private CustomHttpProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new CustomHttpProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
