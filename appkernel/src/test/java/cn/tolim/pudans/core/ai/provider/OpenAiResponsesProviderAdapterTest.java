package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.sse;
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

class OpenAiResponsesProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "resp_1",
              "model": "gpt-5-mini",
              "status": "completed",
              "output": [
                {"type": "reasoning", "summary": [{"type": "summary_text", "text": "Let me think"}]},
                {"type": "message", "role": "assistant",
                 "content": [{"type": "output_text", "text": "Hello"}]},
                {"type": "function_call", "call_id": "call_1", "name": "get_weather",
                 "arguments": "{\\"city\\":\\"Paris\\"}"}
              ],
              "usage": {"input_tokens": 12, "output_tokens": 7,
                        "input_tokens_details": {"cached_tokens": 3}}
            }
            """;

    private static final String TEXT_DELTA = """
            {"type":"response.output_text.delta","delta":"Hel"}
            """;

    private static final String ITEM_ADDED = """
            {"type":"response.output_item.added","output_index":1,
            "item":{"type":"function_call","call_id":"call_1","name":"get_weather"}}
            """;

    private static final String ARGUMENTS_DELTA = """
            {"type":"response.function_call_arguments.delta","output_index":1,
            "item_id":"call_1","delta":"{\\"city\\":\\"Par"}
            """;

    private static final String COMPLETED = """
            {"type":"response.completed","response":{"status":"completed",
            "usage":{"input_tokens":5,"output_tokens":2}}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.OPENAI_RESPONSES);
    }

    @Test
    void buildsHeadersWithBearerAuthAndStreamAccept() {
        var provider = provider(AiProvider.Protocol.OPENAI_RESPONSES);
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");

        var headers = adapter(json("{}")).buildHeaders(provider, "sk-secret", true);

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        assertThat(headers.getFirst("X-Org-Id")).isEqualTo("org-1");
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = adapter(json("{}")).parseChatResponse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("resp_1");
        assertThat(response.model()).isEqualTo("gpt-5-mini");
        assertThat(response.finishReason()).isEqualTo("completed");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.message().parts())
                .hasSize(3)
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                                assertThat(thinking.text()).isEqualTo("Let me think")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.ToolCall.class, call -> {
                            assertThat(call.id()).isEqualTo("call_1");
                            assertThat(call.name()).isEqualTo("get_weather");
                            assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                        }));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(12, 7, 3, null));
    }

    @Test
    void parsesStreamEvents() {
        var adapter = adapter(json("{}"));

        assertThat(adapter.parseStreamEvent(TEXT_DELTA).orElseThrow().content()).isEqualTo("Hel");

        var started = adapter.parseStreamEvent(ITEM_ADDED).orElseThrow().toolCall();
        assertThat(started.index()).isEqualTo(1);
        assertThat(started.id()).isEqualTo("call_1");
        assertThat(started.name()).isEqualTo("get_weather");

        var delta = adapter.parseStreamEvent(ARGUMENTS_DELTA).orElseThrow().toolCall();
        assertThat(delta.index()).isEqualTo(1);
        assertThat(delta.id()).isEqualTo("call_1");
        assertThat(delta.arguments()).isEqualTo("{\"city\":\"Par");

        var completed = adapter.parseStreamEvent(COMPLETED).orElseThrow();
        assertThat(completed.finishReason()).isEqualTo("completed");
        assertThat(completed.usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        assertThat(adapter.parseStreamEvent("{\"type\":\"response.created\"}")).isEmpty();
        assertThat(adapter.parseStreamEvent(" ")).isEmpty();
    }

    @Test
    void rejectsFailedStreamEvent() {
        var adapter = adapter(json("{}"));

        assertThatThrownBy(() -> adapter.parseStreamEvent(
                        "{\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"boom\"}}}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void postsRequestWithSerializedIr() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.OPENAI_RESPONSES), account(),
                        model("gpt-5-mini", 1024), toolRequest(mapper))
                .block();

        assertThat(response).isNotNull();
        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString()).isEqualTo("https://api.example.com/v1/responses");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        var body = body(httpRequest);
        assertThat(body).contains("\"model\":\"gpt-5-mini\"");
        assertThat(body).contains("\"max_output_tokens\":1024");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"instructions\":\"You are helpful.\"");
        assertThat(body).contains("\"content\":\"hello\"");
        assertThat(body).contains("\"type\":\"function_call\"");
        assertThat(body).contains("\"type\":\"function_call_output\"");
        assertThat(body).contains("\"call_id\":\"call_1\"");
        assertThat(body).contains("\"name\":\"get_weather\"");
        assertThat(body).contains("\"temperature\":0.2");
    }

    @Test
    void rejectsMalformedResponse() {
        assertThatThrownBy(() -> adapter(json("{}")).parseChatResponse("{\"id\":\"resp_1\"}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("output");
    }

    @Test
    void rejectsInvalidBaseUrlWithConfigCode() {
        var provider = provider(AiProvider.Protocol.OPENAI_RESPONSES);
        provider.getSpec().setBaseUrl("https://api.example.com?token=1");

        assertThatThrownBy(() -> adapter(json("{}"))
                        .chat(provider, account(), model("gpt-5-mini", null), request())
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(AiProviderConfigSupport.CODE_INVALID);
                });
    }

    @Test
    void streamsSseOverWebClient() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        var events = adapter(sse(TEXT_DELTA, ITEM_ADDED, ARGUMENTS_DELTA, COMPLETED))
                .streamChat(provider(AiProvider.Protocol.OPENAI_RESPONSES), account(),
                        model("gpt-5-mini", null), request())
                .collectList()
                .block();

        assertThat(events).hasSize(4);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).toolCall().name()).isEqualTo("get_weather");
        assertThat(events.get(2).toolCall().arguments()).isEqualTo("{\"city\":\"Par");
        assertThat(events.get(3).usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    private OpenAiResponsesProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new OpenAiResponsesProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
