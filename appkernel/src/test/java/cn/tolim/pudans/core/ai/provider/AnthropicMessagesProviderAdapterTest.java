package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AnthropicMessagesProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "msg_1",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-5",
              "content": [
                {"type": "thinking", "thinking": "Let me think", "signature": "sig-1"},
                {"type": "text", "text": "Hello"},
                {"type": "tool_use", "id": "toolu_1", "name": "get_weather", "input": {"city": "Paris"}},
                {"type": "redacted_thinking", "data": "opaque"}
              ],
              "stop_reason": "tool_use",
              "usage": {
                "input_tokens": 10,
                "output_tokens": 20,
                "cache_read_input_tokens": 4,
                "cache_creation_input_tokens": 2
              }
            }
            """;

    private static final String MESSAGE_START = """
            {"type":"message_start","message":{"id":"msg_1","model":"claude-sonnet-4-5",
            "usage":{"input_tokens":10,"output_tokens":1,"cache_read_input_tokens":4,
            "cache_creation_input_tokens":2}}}
            """;

    private static final String TOOL_BLOCK_START = """
            {"type":"content_block_start","index":1,
            "content_block":{"type":"tool_use","id":"toolu_1","name":"get_weather","input":{}}}
            """;

    private static final String TEXT_DELTA = """
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
            """;

    private static final String THINKING_DELTA = """
            {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}
            """;

    private static final String INPUT_JSON_DELTA = """
            {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta",
            "partial_json":"{\\"city\\""}}
            """;

    private static final String MESSAGE_DELTA = """
            {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},
            "usage":{"output_tokens":20}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.ANTHROPIC);
    }

    @Test
    void buildsHeadersWithApiKeyVersionAndStreamAccept() {
        var provider = provider(AiProvider.Protocol.ANTHROPIC);
        provider.getSpec().getHeaders().put("anthropic-beta", "prompt-caching-2024-07-31");

        var headers = adapter(json("{}")).buildHeaders(provider, "sk-ant-secret", true);

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(headers.getFirst("x-api-key")).isEqualTo("sk-ant-secret");
        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(headers.getFirst("anthropic-beta")).isEqualTo("prompt-caching-2024-07-31");
    }

    @Test
    void omitsApiKeyWhenMissingButKeepsAnthropicVersion() {
        var headers = adapter(json("{}")).buildHeaders(provider(AiProvider.Protocol.ANTHROPIC), null, false);

        assertThat(headers.containsHeader("x-api-key")).isFalse();
        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = adapter(json("{}")).parseChatResponse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("msg_1");
        assertThat(response.model()).isEqualTo("claude-sonnet-4-5");
        assertThat(response.finishReason()).isEqualTo("tool_use");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.message().parts())
                .hasSize(4)
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking -> {
                            assertThat(thinking.text()).isEqualTo("Let me think");
                            assertThat(thinking.signature()).isEqualTo("sig-1");
                        }))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.ToolCall.class, call -> {
                            assertThat(call.id()).isEqualTo("toolu_1");
                            assertThat(call.name()).isEqualTo("get_weather");
                            assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                        }))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Custom.class, custom ->
                                assertThat(custom.type()).isEqualTo("redacted_thinking")));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, 2));
    }

    @Test
    void parsesStreamEvents() {
        var adapter = adapter(json("{}"));

        var start = adapter.parseStreamEvent(MESSAGE_START).orElseThrow();
        assertThat(start.usage()).isEqualTo(new AiTokenUsage(10, 1, 4, 2));

        var toolCall = adapter.parseStreamEvent(TOOL_BLOCK_START).orElseThrow().toolCall();
        assertThat(toolCall.index()).isEqualTo(1);
        assertThat(toolCall.id()).isEqualTo("toolu_1");
        assertThat(toolCall.name()).isEqualTo("get_weather");

        assertThat(adapter.parseStreamEvent(TEXT_DELTA).orElseThrow().content()).isEqualTo("Hello");
        assertThat(adapter.parseStreamEvent(THINKING_DELTA).orElseThrow().reasoning()).isEqualTo("hmm");

        var partial = adapter.parseStreamEvent(INPUT_JSON_DELTA).orElseThrow().toolCall();
        assertThat(partial.index()).isEqualTo(1);
        assertThat(partial.arguments()).isEqualTo("{\"city\"");

        var delta = adapter.parseStreamEvent(MESSAGE_DELTA).orElseThrow();
        assertThat(delta.finishReason()).isEqualTo("end_turn");
        assertThat(delta.usage()).isEqualTo(new AiTokenUsage(null, 20, null, null));

        assertThat(adapter.parseStreamEvent("{\"type\":\"ping\"}")).isEmpty();
        assertThat(adapter.parseStreamEvent("{\"type\":\"message_stop\"}")).isEmpty();
        assertThat(adapter.parseStreamEvent(" ")).isEmpty();
    }

    @Test
    void rejectsErrorStreamEvent() {
        var adapter = adapter(json("{}"));

        assertThatThrownBy(() -> adapter.parseStreamEvent(
                        "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("busy");
    }

    @Test
    void postsChatRequestWithModelAndDefaultMaxTokens() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ant-secret"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.ANTHROPIC), account(),
                        model("claude-sonnet-4-5", 2048), request())
                .block();

        assertThat(response).isNotNull();
        assertThat(response.message().parts())
                .filteredOn(AiContentPart.Text.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                        assertThat(text.text()).isEqualTo("Hello"));

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString()).isEqualTo("https://api.example.com/v1/messages");
        assertThat(httpRequest.headers().getFirst("x-api-key")).isEqualTo("sk-ant-secret");
        assertThat(httpRequest.headers().getFirst("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        var body = body(httpRequest);
        assertThat(body).contains("\"model\":\"claude-sonnet-4-5\"");
        assertThat(body).contains("\"max_tokens\":2048");
        assertThat(body).contains("\"stream\":false");
    }

    @Test
    void streamsSseEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ant-secret"));

        var events = adapter(sse(MESSAGE_START, TEXT_DELTA, MESSAGE_DELTA,
                        "{\"type\":\"message_stop\"}", "{\"type\":\"ping\"}"))
                .streamChat(provider(AiProvider.Protocol.ANTHROPIC), account(),
                        model("claude-sonnet-4-5", 2048), request())
                .collectList()
                .block();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).usage()).isEqualTo(new AiTokenUsage(10, 1, 4, 2));
        assertThat(events.get(1).content()).isEqualTo("Hello");
        assertThat(events.get(2).finishReason()).isEqualTo("end_turn");
        assertThat(events.get(2).usage()).isEqualTo(new AiTokenUsage(null, 20, null, null));

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    private AnthropicMessagesProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new AnthropicMessagesProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static ClientResponse sse(String... data) {
        var body = new StringBuilder();
        for (var item : data) {
            for (var line : item.strip().split("\n")) {
                body.append("data: ").append(line).append('\n');
            }
            body.append('\n');
        }
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(body.toString())
                .build();
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }
}
