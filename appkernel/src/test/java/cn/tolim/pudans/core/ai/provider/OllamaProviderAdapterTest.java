package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.ndjson;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.toolRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class OllamaProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "model": "llama3.1",
              "created_at": "2025-01-01T00:00:00Z",
              "message": {
                "role": "assistant",
                "content": "Hello",
                "thinking": "hmm",
                "tool_calls": [
                  {"function": {"name": "get_weather", "arguments": {"city": "Paris"}}}
                ]
              },
              "done": true,
              "done_reason": "stop",
              "prompt_eval_count": 12,
              "eval_count": 7
            }
            """;

    private static final String CONTENT_LINE = """
            {"message":{"role":"assistant","content":"Hel"},"done":false}
            """;

    private static final String SECOND_CONTENT_LINE = """
            {"message":{"role":"assistant","content":"lo"},"done":false}
            """;

    private static final String TOOL_LINE = "{\"message\":{\"role\":\"assistant\",\"content\":\"\","
            + "\"tool_calls\":[{\"function\":{\"name\":\"get_weather\","
            + "\"arguments\":{\"city\":\"Paris\"}}}]},\"done\":false}";

    private static final String DONE_LINE = "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},"
            + "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":5,\"eval_count\":2}";

    private static final String NDJSON_BODY = String.join(
            "\n", CONTENT_LINE.strip(), SECOND_CONTENT_LINE.strip(), TOOL_LINE, DONE_LINE) + "\n";

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.OLLAMA);
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = adapter(json("{}")).parseChatResponse(RESPONSE_BODY);

        assertThat(response.model()).isEqualTo("llama3.1");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.message().parts())
                .hasSize(3)
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                                assertThat(thinking.text()).isEqualTo("hmm")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.ToolCall.class, call -> {
                            assertThat(call.name()).isEqualTo("get_weather");
                            assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                        }));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(12, 7, null, null));
    }

    @Test
    void parsesNdjsonLines() {
        var adapter = adapter(json("{}"));

        var content = adapter.parseStreamEvents(CONTENT_LINE).get(0);
        assertThat(content.content()).isEqualTo("Hel");

        var tool = adapter.parseStreamEvents(TOOL_LINE).get(0).toolCall();
        assertThat(tool.index()).isZero();
        assertThat(tool.name()).isEqualTo("get_weather");
        assertThat(tool.arguments()).isEqualTo("{\"city\":\"Paris\"}");

        var done = adapter.parseStreamEvents(DONE_LINE).get(0);
        assertThat(done.finishReason()).isEqualTo("stop");
        assertThat(done.usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        assertThat(adapter.parseStreamEvents(" ")).isEmpty();
    }

    @Test
    void rejectsStreamErrorLine() {
        var adapter = adapter(json("{}"));

        assertThatThrownBy(() -> adapter.parseStreamEvents("{\"error\":\"model not found\"}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("model not found");
    }

    @Test
    void postsChatRequestWithOptionsAndTools() {
        when(accountService.apiKey(any())).thenReturn(Mono.empty());

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.OLLAMA), account(),
                        model("llama3.1", 512), toolRequest(mapper))
                .block();

        assertThat(response).isNotNull();
        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString()).isEqualTo("https://api.example.com/api/chat");
        var body = body(httpRequest);
        assertThat(body).contains("\"model\":\"llama3.1\"");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"temperature\":0.2");
        assertThat(body).contains("\"num_predict\":512");
        assertThat(body).contains("\"tools\":[{\"type\":\"function\"");
        assertThat(body).contains("\"name\":\"get_weather\"");
        assertThat(body).contains("\"description\":\"Weather lookup\"");
        assertThat(body).contains("\"tool_calls\"");
        assertThat(body).contains("\"role\":\"tool\"");
        assertThat(body).contains("\"tool_name\":\"get_weather\"");
    }

    @Test
    void streamsNdjsonOverWebClient() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("ollama-secret"));

        var events = adapter(ndjson(NDJSON_BODY))
                .streamChat(provider(AiProvider.Protocol.OLLAMA), account(),
                        model("llama3.1", null), request())
                .collectList()
                .block();

        assertThat(events).hasSize(4);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).content()).isEqualTo("lo");
        assertThat(events.get(2).toolCall().name()).isEqualTo("get_weather");
        assertThat(events.get(3).finishReason()).isEqualTo("stop");
        assertThat(events.get(3).usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.APPLICATION_NDJSON_VALUE);
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    @Test
    void rejectsErrorBody() {
        assertThatThrownBy(() -> adapter(json("{}")).parseChatResponse("{\"error\":\"model not found\"}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("model not found");
    }

    @Test
    void rejectsResponseWithoutMessage() {
        assertThatThrownBy(() -> adapter(json("{}")).parseChatResponse("{\"model\":\"llama3.1\"}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("message");
    }

    @Test
    void rejectsMissingBaseUrlWithConfigCode() {
        var provider = provider(AiProvider.Protocol.OLLAMA);
        provider.getSpec().setBaseUrl(null);

        assertThatThrownBy(() -> adapter(json("{}"))
                        .chat(provider, account(), model("llama3.1", null), request())
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(AiProviderConfigSupport.CODE_INVALID);
                });
    }

    private OllamaProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new OllamaProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
