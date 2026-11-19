package cn.tolim.pudans.core.ai.provider;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
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

class OpenAiChatProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "chatcmpl-1",
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello",
                    "reasoning_content": "Let me think",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "get_weather",
                          "arguments": "{\\"city\\":\\"Paris\\"}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 7,
                "prompt_tokens_details": {"cached_tokens": 3}
              }
            }
            """;

    private static final String CONTENT_CHUNK = """
            {"choices":[{"index":0,"delta":{"content":"Hel"}}]}
            """;

    private static final String TOOL_CHUNK = """
            {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1",
            "function":{"name":"get_weather","arguments":"{\\"city\\":\\"Par"}}]}}]}
            """;

    private static final String FINISH_CHUNK = """
            {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],
            "usage":{"prompt_tokens":5,"completion_tokens":2}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.OPENAI_CHAT);
    }

    @Test
    void buildsHeadersWithBearerAuthAndStreamAccept() {
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");

        var headers = adapter(json("{}")).buildHeaders(provider, "sk-secret", true);

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        assertThat(headers.getFirst("X-Org-Id")).isEqualTo("org-1");
    }

    @Test
    void omitsAuthorizationWhenApiKeyIsMissing() {
        var headers = adapter(json("{}")).buildHeaders(provider(AiProvider.Protocol.OPENAI_CHAT), null, false);

        assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = adapter(json("{}")).parseChatResponse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("chatcmpl-1");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.finishReason()).isEqualTo("tool_calls");
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
    void parsesStreamEventsAndIgnoresDone() {
        var adapter = adapter(json("{}"));

        var content = adapter.parseStreamEvent(CONTENT_CHUNK).orElseThrow();
        assertThat(content.content()).isEqualTo("Hel");

        var toolCall = adapter.parseStreamEvent(TOOL_CHUNK).orElseThrow().toolCall();
        assertThat(toolCall.index()).isZero();
        assertThat(toolCall.id()).isEqualTo("call_1");
        assertThat(toolCall.name()).isEqualTo("get_weather");
        assertThat(toolCall.arguments()).isEqualTo("{\"city\":\"Par");

        var finish = adapter.parseStreamEvent(FINISH_CHUNK).orElseThrow();
        assertThat(finish.finishReason()).isEqualTo("stop");
        assertThat(finish.usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        assertThat(adapter.parseStreamEvent("[DONE]")).isEmpty();
        assertThat(adapter.parseStreamEvent(" ")).isEmpty();
        assertThat(adapter.parseStreamEvent("{\"choices\":[]}")).isEmpty();
    }

    @Test
    void postsChatRequestWithModelAndBearerKey() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.OPENAI_CHAT), account(), model("gpt-4o-mini", null), request())
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
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        var body = body(httpRequest);
        assertThat(body).contains("\"model\":\"gpt-4o-mini\"");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"content\":\"hello\"");
    }

    @Test
    void streamsSseDataUntilDone() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));

        var events = adapter(sse(CONTENT_CHUNK, TOOL_CHUNK, FINISH_CHUNK, "[DONE]"))
                .streamChat(provider(AiProvider.Protocol.OPENAI_CHAT), account(), model("gpt-4o-mini", null),
                        request())
                .collectList()
                .block();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).toolCall().id()).isEqualTo("call_1");
        assertThat(events.get(2).finishReason()).isEqualTo("stop");
        assertThat(events.get(2).usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    private OpenAiChatProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new OpenAiChatProviderAdapter(
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
