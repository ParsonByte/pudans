package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

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
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
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

class DeepSeekProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "msg-ds-1",
              "type": "message",
              "role": "assistant",
              "model": "deepseek-flash",
              "content": [
                {"type": "thinking", "thinking": "Let me think", "signature": "sig-1"},
                {"type": "text", "text": "Hello", "citations": [
                  {"type": "web_search_result_location", "url": "https://example.com",
                   "title": "Example", "cited_text": "Hello", "encrypted_index": ""}
                ]},
                {"type": "tool_use", "id": "toolu_1", "name": "get_weather",
                 "input": {"city": "Paris"}},
                {"type": "server_tool_use", "id": "srvtoolu_s_1",
                 "name": "deepseek_web_search",
                 "input": {"action": "search", "queries": ["weather"]},
                 "caller": {"type": "direct"}, "kind": "search"},
                {"type": "web_search_tool_result", "tool_use_id": "srvtoolu_s_1",
                 "content": [], "kind": "search"},
                {"type": "web_fetch_tool_result", "tool_use_id": "srvtoolu_rl_1",
                 "content": {"type": "web_fetch_tool_result_error",
                 "error_code": "url_not_accessible"}, "kind": "read_link"}
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
            {"type":"message_start","message":{"id":"msg-ds-1","model":"deepseek-flash",
            "usage":{"input_tokens":10,"output_tokens":0,"cache_read_input_tokens":4,
            "cache_creation_input_tokens":2}}}
            """;

    private static final String TOOL_BLOCK_START = """
            {"type":"content_block_start","index":1,
            "content_block":{"type":"tool_use","id":"toolu_1","name":"get_weather","input":{}}}
            """;

    private static final String SERVER_TOOL_BLOCK_START = """
            {"type":"content_block_start","index":2,
            "content_block":{"type":"server_tool_use","id":"srvtoolu_1",
            "name":"deepseek_web_search","input":{"action":"search","queries":["weather"]},
            "kind":"search"}}
            """;

    private static final String TEXT_DELTA = """
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
            """;

    private static final String THINKING_DELTA = """
            {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}
            """;

    private static final String SIGNATURE_DELTA = """
            {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig"}}
            """;

    private static final String CITATIONS_DELTA = """
            {"type":"content_block_delta","index":0,"delta":{"type":"citations_delta",
            "citation":{"type":"web_search_result_location","url":"https://example.com",
            "title":"Example","cited_text":"Hello","encrypted_index":""}}}
            """;

    private static final String INPUT_JSON_DELTA = """
            {"type":"content_block_delta","index":1,
            "delta":{"type":"input_json_delta","partial_json":"{\\"city\\""}}
            """;

    private static final String MESSAGE_DELTA = """
            {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},
            "usage":{"output_tokens":20}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesDeepSeekProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.DEEPSEEK);
    }

    @Test
    void buildsHeadersWithApiKeyStreamAcceptAndProviderHeaders() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        provider.getSpec().getHeaders().put("X-Trace", "trace-1");

        var headers = adapter(json("{}")).buildHeaders(provider, "sk-ds-secret", true);

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(headers.getFirst(DeepSeekProtocol.API_KEY_HEADER)).isEqualTo("sk-ds-secret");
        assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(headers.getFirst("X-Trace")).isEqualTo("trace-1");
    }

    @Test
    void omitsApiKeyHeaderWhenKeyIsMissing() {
        var headers = adapter(json("{}"))
                .buildHeaders(provider(AiProvider.Protocol.DEEPSEEK), null, false);

        assertThat(headers.containsHeader(DeepSeekProtocol.API_KEY_HEADER)).isFalse();
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = parser().parse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("msg-ds-1");
        assertThat(response.model()).isEqualTo("deepseek-flash");
        assertThat(response.finishReason()).isEqualTo("tool_use");
        assertThat(response.message().role()).isEqualTo("assistant");
        assertThat(response.message().parts())
                .hasSize(6)
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
                                assertThat(custom.type()).isEqualTo("server_tool_use")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Custom.class, custom ->
                                assertThat(custom.type()).isEqualTo("web_search_tool_result")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Custom.class, custom ->
                                assertThat(custom.type()).isEqualTo("web_fetch_tool_result")));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(10, 20, 4, 2));
    }

    @Test
    void rejectsResponseWithoutContentArray() {
        var parser = parser();

        assertThatThrownBy(() -> parser.parse("{\"id\":\"msg-ds-1\"}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("content");
        assertThatThrownBy(() -> parser.parse("[1,2]"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("JSON 对象");
    }

    @Test
    void parsesStreamEvents() {
        var parser = parser();

        assertThat(parser.parseStreamEvent(MESSAGE_START).orElseThrow().usage())
                .isEqualTo(new AiTokenUsage(10, 0, 4, 2));

        var toolCall = parser.parseStreamEvent(TOOL_BLOCK_START).orElseThrow().toolCall();
        assertThat(toolCall.index()).isEqualTo(1);
        assertThat(toolCall.id()).isEqualTo("toolu_1");
        assertThat(toolCall.name()).isEqualTo("get_weather");

        assertThat(parser.parseStreamEvent(SERVER_TOOL_BLOCK_START)).isEmpty();
        assertThat(parser.parseStreamEvent(TEXT_DELTA).orElseThrow().content()).isEqualTo("Hello");
        assertThat(parser.parseStreamEvent(THINKING_DELTA).orElseThrow().reasoning()).isEqualTo("hmm");
        assertThat(parser.parseStreamEvent(SIGNATURE_DELTA)).isEmpty();
        assertThat(parser.parseStreamEvent(CITATIONS_DELTA)).isEmpty();

        var partial = parser.parseStreamEvent(INPUT_JSON_DELTA).orElseThrow().toolCall();
        assertThat(partial.index()).isEqualTo(1);
        assertThat(partial.arguments()).isEqualTo("{\"city\"");

        var delta = parser.parseStreamEvent(MESSAGE_DELTA).orElseThrow();
        assertThat(delta.finishReason()).isEqualTo("end_turn");
        assertThat(delta.usage()).isEqualTo(new AiTokenUsage(null, 20, null, null));

        assertThat(parser.parseStreamEvent("{\"type\":\"ping\"}")).isEmpty();
        assertThat(parser.parseStreamEvent("{\"type\":\"message_stop\"}")).isEmpty();
        assertThat(parser.parseStreamEvent("{\"type\":\"content_block_stop\",\"index\":0}")).isEmpty();
        assertThat(parser.parseStreamEvent(" ")).isEmpty();
    }

    @Test
    void rejectsStreamErrorFrames() {
        var parser = parser();

        assertThatThrownBy(() -> parser.parseStreamEvent(
                        "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("busy");
        assertThatThrownBy(() -> parser.parseStreamEvent("{\"error\":{\"message\":\"timeout\"}}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void postsChatRequestWithDeepSeekExtensions() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.DEEPSEEK), account(),
                        model("deepseek-flash", 2048), richRequest())
                .block();

        assertThat(response).isNotNull();
        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/anthropic/v1/messages");
        assertThat(httpRequest.headers().getFirst(DeepSeekProtocol.API_KEY_HEADER))
                .isEqualTo("sk-ds-secret");
        assertThat(httpRequest.headers().containsHeader(DeepSeekProtocol.BETA_HEADER)).isFalse();

        var body = body(httpRequest);
        assertThat(body).contains("\"model\":\"deepseek-flash\"");
        assertThat(body).contains("\"system\":\"You are helpful.\"");
        assertThat(body).contains("\"max_tokens\":2048");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"temperature\":0.2");
        assertThat(body).contains("\"top_p\":0.9");
        assertThat(body).contains("\"top_k\":5");
        assertThat(body).contains("\"stop_sequences\":[\"END\"]");
        assertThat(body).contains("\"tool_choice\":{\"type\":\"auto\"}");
        assertThat(body).contains("\"thinking\":{\"type\":\"enabled\"}");
        assertThat(body).contains("\"safety_check\":{\"level\":\"high\"}");
        assertThat(body).contains("\"title\":{\"enabled\":true}");
        assertThat(body).contains(
                "\"type\":\"deepseek_web_search\",\"name\":\"deepseek_web_search\"");
        assertThat(body).contains("\"name\":\"get_weather\",\"description\":\"Weather lookup\"");
        assertThat(body).contains("\"role\":\"latest_reminder\",\"content\":\"location: CN\"");
        assertThat(body).contains(
                "\"type\":\"thinking\",\"thinking\":\"checking\",\"signature\":\"sig-1\"");
        assertThat(body).contains(
                "\"type\":\"tool_use\",\"id\":\"call_1\",\"name\":\"get_weather\","
                        + "\"input\":{\"city\":\"Paris\"}");
        assertThat(body).contains(
                "\"type\":\"tool_result\",\"tool_use_id\":\"call_1\","
                        + "\"content\":{\"temperature\":20}");
    }

    @Test
    void sendsFilesBetaHeaderAndFileSource() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));
        var file = new AiContentPart.File(
                "application/pdf", null, null, "report.pdf", "file-api-1", null);
        var imageFile = new AiContentPart.File(
                "image/png", null, null, "chart.png", "file-api-2", null);
        var imageRaw = mapper.createObjectNode().put("type", "image");
        imageRaw.putObject("source").put("type", "file").put("file_id", "file-api-3");
        var image = new AiContentPart.Custom("image", imageRaw);
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(
                        new AiContentPart.Text("read this", null), file, imageFile, image))),
                List.of(),
                new AiRequestParams("requested-model", null, null, null, null, null, null, Map.of()),
                false);

        adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.DEEPSEEK), account(), model("deepseek-flash", null), request)
                .block();

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(DeepSeekProtocol.BETA_HEADER))
                .isEqualTo(DeepSeekProtocol.FILES_BETA);
        var body = body(httpRequest);
        assertThat(body).contains(
                "\"type\":\"document\",\"source\":{\"type\":\"file\",\"file_id\":\"file-api-1\"}");
        assertThat(body).contains(
                "\"type\":\"image\",\"source\":{\"type\":\"file\",\"file_id\":\"file-api-2\"}");
        assertThat(body).contains(
                "\"type\":\"image\",\"source\":{\"type\":\"file\",\"file_id\":\"file-api-3\"}");
    }

    @Test
    void rejectsDocumentWithoutFileId() {
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.File(
                        "application/pdf", "aGVsbG8=", null, "report.pdf", null, null)))),
                List.of(),
                new AiRequestParams("requested-model", null, null, null, null, null, null, Map.of()),
                false);

        assertThatThrownBy(() -> adapter(json(RESPONSE_BODY))
                        .chat(provider(AiProvider.Protocol.DEEPSEEK), account(),
                                model("deepseek-flash", null), request)
                        .block())
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("file_id");
    }

    @Test
    void serializesImageSources() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));
        var request = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(
                        new AiContentPart.Image("image/png", "aGVsbG8=", null, null, null),
                        new AiContentPart.Image(null, null, "https://example.com/a.png", null, null)))),
                List.of(),
                new AiRequestParams("requested-model", null, null, null, null, null, null, Map.of()),
                false);

        adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.DEEPSEEK), account(), model("deepseek-flash", null), request)
                .block();

        var body = body(captured.get());
        assertThat(body).contains("\"type\":\"image\",\"source\":{\"type\":\"base64\","
                + "\"media_type\":\"image/png\",\"data\":\"aGVsbG8=\"}");
        assertThat(body).contains("\"type\":\"image\",\"source\":{\"type\":\"url\","
                + "\"url\":\"https://example.com/a.png\"}");
    }

    @Test
    void streamsSseEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var events = adapter(sse(MESSAGE_START, TEXT_DELTA, MESSAGE_DELTA,
                        "{\"type\":\"message_stop\"}", "{\"type\":\"ping\"}"))
                .streamChat(provider(AiProvider.Protocol.DEEPSEEK), account(),
                        model("deepseek-flash", 2048), request())
                .collectList()
                .block();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).usage()).isEqualTo(new AiTokenUsage(10, 0, 4, 2));
        assertThat(events.get(1).content()).isEqualTo("Hello");
        assertThat(events.get(2).finishReason()).isEqualTo("end_turn");

        var httpRequest = captured.get();
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(body(httpRequest)).contains("\"stream\":true");
    }

    private DeepSeekMessagesParser parser() {
        return new DeepSeekMessagesParser(mapper);
    }

    private AiChatIr richRequest() {
        var parameters = mapper.createObjectNode().put("type", "object");
        var toolArguments = mapper.createObjectNode().put("city", "Paris");
        var toolResult = mapper.createObjectNode().put("temperature", 20);
        Map<String, JsonNode> extras = Map.of(
                "thinking", mapper.createObjectNode().put("type", "enabled"),
                "safety_check", mapper.createObjectNode().put("level", "high"),
                "title", mapper.createObjectNode().put("enabled", true));
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
                        new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null))),
                        new AiChatMessage(
                                "latest_reminder",
                                List.of(new AiContentPart.Text("location: CN", null))),
                        new AiChatMessage("assistant", List.of(
                                new AiContentPart.Thinking("checking", "sig-1", null),
                                new AiContentPart.Text("checking", null),
                                new AiContentPart.ToolCall("call_1", "get_weather", toolArguments, null))),
                        new AiChatMessage(
                                "user", List.of(new AiContentPart.ToolResult("call_1", toolResult, null, null)))),
                List.of(
                        new AiTool("deepseek_web_search", null, null, null),
                        new AiTool("get_weather", "Weather lookup", parameters, null)),
                new AiRequestParams(
                        "requested-model",
                        0.2,
                        null,
                        0.9,
                        5,
                        List.of("END"),
                        mapper.createObjectNode().put("type", "auto"),
                        extras),
                false);
    }

    private DeepSeekProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new DeepSeekProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
