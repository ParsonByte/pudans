package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
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
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
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

class MinimaxChatProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "cmpl-mm-1",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello",
                  "reasoning_content": "Let me think",
                  "tool_calls": [{"id": "call_1", "type": "function",
                    "function": {"name": "get_weather", "arguments": "{\\"city\\":\\"Paris\\"}"}}]
                },
                "finish_reason": "tool_calls"
              }],
              "created": 1700000000,
              "model": "MiniMax-M3.1-Flash",
              "object": "chat.completion",
              "usage": {"total_tokens": 30}
            }
            """;

    private static final String CONTENT_CHUNK = """
            {"choices":[{"index":0,"delta":{"content":"Hel"}}]}
            """;

    private static final String REASONING_CHUNK = """
            {"choices":[{"index":0,"delta":{"reasoning_content":"hmm"}}]}
            """;

    private static final String FINISH_CHUNK = """
            {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],
            "usage":{"total_tokens":12}}
            """;

    private static final String ERROR_BODY = """
            {"base_resp":{"status_code":1008,"status_msg":"insufficient balance"}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesMinimaxChatProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.MINIMAX_CHAT);
    }

    @Test
    void parsesResponseWithReasoningAndToolCalls() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));
        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.MINIMAX_CHAT), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .block();

        assertThat(response.id()).isEqualTo("cmpl-mm-1");
        assertThat(response.finishReason()).isEqualTo("tool_calls");
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
                        }));
    }

    @Test
    void rejectsBaseRespErrors() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        assertThatThrownBy(() -> adapter(json(ERROR_BODY))
                        .chat(provider(AiProvider.Protocol.MINIMAX_CHAT), account(),
                                model("MiniMax-M3.1-Flash", 2048), request())
                        .block())
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("insufficient balance");
    }

    @Test
    void postsChatRequestWithFilesAndExtras() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.MINIMAX_CHAT), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .block();

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/v1/chat/completions");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer mm-key");
        var body = body(httpRequest);
        assertThat(body)
                .contains("\"model\":\"MiniMax-M3.1-Flash\"")
                .contains("\"reasoning_split\":true")
                .contains("\"service_tier\":\"standard\"")
                .contains("\"type\":\"image_url\",\"image_url\":{\"url\":\"mm_file://file-1\"}")
                .contains("\"type\":\"video_url\",\"video_url\":{\"url\":\"https://example.com/v.mp4\"}")
                .doesNotContain("\"type\":\"file\"");
    }

    @Test
    void parsesStreamEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var events = adapter(sse(CONTENT_CHUNK, REASONING_CHUNK, FINISH_CHUNK, "[DONE]"))
                .streamChat(provider(AiProvider.Protocol.MINIMAX_CHAT), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .collectList()
                .block();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).reasoning()).isEqualTo("hmm");
        assertThat(events.get(2).finishReason()).isEqualTo("stop");
    }

    @Test
    void streamsSseEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var events = adapter(sse(CONTENT_CHUNK, FINISH_CHUNK, "[DONE]"))
                .streamChat(provider(AiProvider.Protocol.MINIMAX_CHAT), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).finishReason()).isEqualTo("stop");
    }

    private AiChatIr request() {
        Map<String, JsonNode> extras = Map.of(
                "reasoning_split", mapper.getNodeFactory().booleanNode(true),
                "service_tier", mapper.getNodeFactory().textNode("standard"));
        return new AiChatIr(
                List.of(new AiChatMessage("user", List.of(
                        new AiContentPart.Text("describe", null),
                        new AiContentPart.File(
                                "image/png", null, null, "a.png", "file-1", null),
                        new AiContentPart.File(
                                "video/mp4", null, "https://example.com/v.mp4", "v.mp4", null, null)))),
                List.of(),
                new AiRequestParams("requested", 0.2, null, null, null, List.of(), null, extras),
                false);
    }

    private MinimaxChatProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new MinimaxChatProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
