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

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeepSeekChatProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "chatcmpl-ds-1",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "deepseek-flash",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello",
                    "reasoning_content": "Let me think",
                    "tool_calls": [
                      {"id": "call_1", "type": "function",
                       "function": {"name": "get_weather", "arguments": "{\\"city\\":\\"Paris\\"}"}}
                    ],
                    "annotations": [{"type": "url_citation", "url": "https://example.com"}],
                    "server_tool_calls": [{"step": 1}],
                    "title": "greeting",
                    "refusal": "blocked"
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "prompt_tokens_details": {"cached_tokens": 4},
                "prompt_cache_hit_tokens": 4,
                "prompt_cache_miss_tokens": 6
              }
            }
            """;

    private static final String CONTENT_CHUNK = """
            {"choices":[{"index":0,"delta":{"content":"Hel"}}]}
            """;

    private static final String REASONING_CHUNK = """
            {"choices":[{"index":0,"delta":{"reasoning_content":"hmm"}}]}
            """;

    private static final String TOOL_CHUNK = """
            {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1",
            "function":{"name":"get_weather","arguments":"{\\"city\\":\\"Par"}}]}}]}
            """;

    private static final String FINISH_CHUNK = """
            {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],
            "usage":{"prompt_tokens":5,"completion_tokens":2,
            "prompt_tokens_details":{"cached_tokens":1}}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesDeepSeekChatProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.DEEPSEEK_CHAT);
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = parser().parse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("chatcmpl-ds-1");
        assertThat(response.model()).isEqualTo("deepseek-flash");
        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.message().parts())
                .hasSize(7)
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
                        }))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Custom.class, custom ->
                                assertThat(custom.type()).isEqualTo("annotations")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Custom.class, custom ->
                                assertThat(custom.type()).isEqualTo("server_tool_calls")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Custom.class, custom ->
                                assertThat(custom.type()).isEqualTo("title")));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, null));
    }

    @Test
    void parsesStreamEventsAndIgnoresDone() {
        var parser = parser();

        assertThat(parser.parseStreamEvent(CONTENT_CHUNK).orElseThrow().content()).isEqualTo("Hel");
        assertThat(parser.parseStreamEvent(REASONING_CHUNK).orElseThrow().reasoning()).isEqualTo("hmm");

        var toolCall = parser.parseStreamEvent(TOOL_CHUNK).orElseThrow().toolCall();
        assertThat(toolCall.index()).isZero();
        assertThat(toolCall.id()).isEqualTo("call_1");
        assertThat(toolCall.name()).isEqualTo("get_weather");
        assertThat(toolCall.arguments()).isEqualTo("{\"city\":\"Par");

        var finish = parser.parseStreamEvent(FINISH_CHUNK).orElseThrow();
        assertThat(finish.finishReason()).isEqualTo("stop");
        assertThat(finish.usage()).isEqualTo(new AiTokenUsage(5, 2, 1, null));

        assertThat(parser.parseStreamEvent("[DONE]")).isEmpty();
        assertThat(parser.parseStreamEvent(" ")).isEmpty();
        assertThat(parser.parseStreamEvent("{\"choices\":[]}")).isEmpty();
    }

    @Test
    void postsChatRequestWithDeepSeekFields() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.DEEPSEEK_CHAT), account(),
                        model("deepseek-flash", 2048), richRequest())
                .block();

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/chat/completions");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer sk-ds-secret");
        assertThat(httpRequest.headers().getFirst(DeepSeekProtocol.API_KEY_HEADER)).isNull();

        var body = body(httpRequest);
        assertThat(body)
                .contains("\"model\":\"deepseek-flash\"")
                .contains("\"max_tokens\":2048")
                .contains("\"stream\":false")
                .contains("\"reasoning_effort\":\"high\"")
                .contains("\"response_format\":{\"type\":\"json_object\"}")
                .contains("\"role\":\"system\",\"content\":\"You are helpful.\"")
                .contains("\"role\":\"latest_reminder\",\"content\":\"2026-11-19,Wednesday,CN,API,zh\"")
                .contains("\"type\":\"deepseek_web_search\"")
                .contains("\"type\":\"function\",\"function\":{\"name\":\"get_weather\"")
                .contains("\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"sunny\"")
                .contains("\"reasoning_content\":\"checking\"")
                .contains("\"type\":\"image_url\"")
                .contains("\"type\":\"file\",\"file_id\":\"file-api-1\"");
    }

    @Test
    void streamsSseEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var events = adapter(sse(CONTENT_CHUNK, FINISH_CHUNK, "[DONE]"))
                .streamChat(provider(AiProvider.Protocol.DEEPSEEK_CHAT), account(),
                        model("deepseek-flash", 2048), richRequest())
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).finishReason()).isEqualTo("stop");

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    private DeepSeekChatParser parser() {
        return new DeepSeekChatParser(mapper);
    }

    private AiChatIr richRequest() {
        Map<String, JsonNode> extras = Map.of(
                "reasoning_effort", mapper.getNodeFactory().textNode("high"),
                "response_format", mapper.createObjectNode().put("type", "json_object"));
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
                        new AiChatMessage(
                                "latest_reminder",
                                List.of(new AiContentPart.Text("2026-11-19,Wednesday,CN,API,zh", null))),
                        new AiChatMessage("user", List.of(
                                new AiContentPart.Text("hello", null),
                                new AiContentPart.Image("image/png", "aGVsbG8=", null, "high", null),
                                new AiContentPart.File(
                                        "application/pdf", null, null, "r.pdf", "file-api-1", null))),
                        new AiChatMessage("assistant", List.of(
                                new AiContentPart.Thinking("checking", null, null),
                                new AiContentPart.Text("checking", null),
                                new AiContentPart.ToolCall(
                                        "call_1",
                                        "get_weather",
                                        mapper.createObjectNode().put("city", "Paris"),
                                        null))),
                        new AiChatMessage(
                                "user",
                                List.of(new AiContentPart.ToolResult(
                                        "call_1",
                                        mapper.getNodeFactory().textNode("sunny"),
                                        null,
                                        null)))),
                List.of(
                        new AiTool("deepseek_web_search", null, null, null),
                        new AiTool(
                                "get_weather",
                                "Weather lookup",
                                mapper.createObjectNode().put("type", "object"),
                                null)),
                new AiRequestParams(
                        "requested-model", 0.2, null, 0.9, null, List.of(), null, extras),
                false);
    }

    private DeepSeekChatProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new DeepSeekChatProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
