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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeepSeekResponsesProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "resp-ds-1",
              "object": "response",
              "created_at": 1700000000,
              "status": "completed",
              "model": "deepseek-flash",
              "output": [
                {"type": "reasoning",
                 "content": [{"type": "reasoning_text", "text": "Let me think"}]},
                {"type": "message", "role": "assistant",
                 "content": [{"type": "output_text", "text": "Hello",
                 "annotations": [{"type": "url_citation", "url": "https://example.com"}]}]},
                {"type": "function_call", "call_id": "call_1", "name": "get_weather",
                 "arguments": "{\\"city\\":\\"Paris\\"}"},
                {"type": "web_search_call", "id": "ws_1", "status": "completed", "kind": "search"}
              ],
              "usage": {
                "input_tokens": 10,
                "output_tokens": 20,
                "input_tokens_details": {"cached_tokens": 4}
              }
            }
            """;

    private static final String TEXT_DELTA = """
            {"type":"response.output_text.delta","delta":"Hello"}
            """;

    private static final String REASONING_DELTA = """
            {"type":"response.reasoning_text.delta","delta":"hmm"}
            """;

    private static final String TOOL_ADDED = """
            {"type":"response.output_item.added","output_index":1,
            "item":{"type":"function_call","call_id":"call_1","name":"get_weather"}}
            """;

    private static final String TOOL_DELTA = """
            {"type":"response.function_call_arguments.delta","output_index":1,
            "item_id":"item_1","delta":"{\\"city\\""}
            """;

    private static final String COMPLETED = """
            {"type":"response.completed","response":{"id":"resp-ds-1","status":"completed",
            "usage":{"input_tokens":10,"output_tokens":20,
            "input_tokens_details":{"cached_tokens":4}}}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesDeepSeekResponsesProtocol() {
        assertThat(adapter(json("{}")).protocol())
                .isEqualTo(AiProvider.Protocol.DEEPSEEK_RESPONSES);
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = parser().parse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("resp-ds-1");
        assertThat(response.model()).isEqualTo("deepseek-flash");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.message().parts())
                .hasSize(5)
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
                                assertThat(custom.type()).isEqualTo("web_search_call")));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, null));
    }

    @Test
    void parsesStreamEvents() {
        var parser = parser();

        assertThat(parser.parseStreamEvent(TEXT_DELTA).orElseThrow().content()).isEqualTo("Hello");
        assertThat(parser.parseStreamEvent(REASONING_DELTA).orElseThrow().reasoning()).isEqualTo("hmm");

        var tool = parser.parseStreamEvent(TOOL_ADDED).orElseThrow().toolCall();
        assertThat(tool.index()).isEqualTo(1);
        assertThat(tool.id()).isEqualTo("call_1");
        assertThat(tool.name()).isEqualTo("get_weather");
        assertThat(parser.parseStreamEvent(TOOL_DELTA).orElseThrow().toolCall().arguments())
                .isEqualTo("{\"city\"");

        var done = parser.parseStreamEvent(COMPLETED).orElseThrow();
        assertThat(done.finishReason()).isEqualTo("stop");
        assertThat(done.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, null));

        assertThat(parser.parseStreamEvent("{\"type\":\"response.created\"}")).isEmpty();
    }

    @Test
    void postsResponsesRequestWithDeepSeekFields() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.DEEPSEEK_RESPONSES), account(),
                        model("deepseek-flash", 2048), richRequest())
                .block();

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString()).isEqualTo("https://api.example.com/responses");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer sk-ds-secret");

        var body = body(httpRequest);
        assertThat(body)
                .contains("\"model\":\"deepseek-flash\"")
                .contains("\"instructions\":\"You are helpful.\"")
                .contains("\"max_output_tokens\":2048")
                .contains("\"stream\":false")
                .contains("\"safety_check\":{\"level\":\"high\"}")
                .contains("\"title\":{\"enabled\":true}")
                .contains("\"reasoning\":{\"effort\":\"high\"}")
                .contains("\"type\":\"input_image\"")
                .contains("\"type\":\"input_file\",\"file_id\":\"file-api-1\"")
                .contains("\"type\":\"reasoning_text\"")
                .contains("\"type\":\"function_call\",\"call_id\":\"call_1\"")
                .contains("\"type\":\"function_call_output\",\"call_id\":\"call_1\"")
                .contains("\"type\":\"function\",\"name\":\"get_weather\"")
                .contains("\"type\":\"web_search\"")
                .contains("\"type\":\"custom\",\"name\":\"apply_patch\"");
    }

    @Test
    void streamsSseEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var events = adapter(sse(TEXT_DELTA, COMPLETED))
                .streamChat(provider(AiProvider.Protocol.DEEPSEEK_RESPONSES), account(),
                        model("deepseek-flash", 2048), richRequest())
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).content()).isEqualTo("Hello");
        assertThat(events.get(1).finishReason()).isEqualTo("stop");
    }

    private DeepSeekResponsesParser parser() {
        return new DeepSeekResponsesParser(mapper);
    }

    private AiChatIr richRequest() {
        Map<String, JsonNode> extras = Map.of(
                "safety_check", mapper.createObjectNode().put("level", "high"),
                "title", mapper.createObjectNode().put("enabled", true),
                "reasoning", mapper.createObjectNode().put("effort", "high"));
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
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
                        new AiTool(
                                "get_weather",
                                "Weather lookup",
                                mapper.createObjectNode().put("type", "object"),
                                null),
                        new AiTool("web_search", null, null, null),
                        new AiTool("apply_patch", null, null, null)),
                new AiRequestParams(
                        "requested-model", 0.2, null, 0.9, null, List.of(), null, extras),
                false);
    }

    private DeepSeekResponsesProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new DeepSeekResponsesProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
