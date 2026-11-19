package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
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

class MinimaxResponsesProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "id": "resp-mm-1",
              "object": "response",
              "created_at": 1700000000,
              "model": "MiniMax-M3.1-Flash",
              "status": "completed",
              "output": [
                {"type": "reasoning", "content": [{"type": "reasoning_text", "text": "think"}]},
                {"type": "message", "role": "assistant",
                 "content": [{"type": "output_text", "text": "Hello"}]},
                {"type": "function_call", "call_id": "call_1", "name": "get_weather",
                 "arguments": "{\\"city\\":\\"Paris\\"}"}
              ],
              "usage": {"input_tokens": 10, "output_tokens": 5,
                "input_tokens_details": {"cached_tokens": 2}}
            }
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesMinimaxResponsesProtocol() {
        assertThat(adapter(json("{}")).protocol())
                .isEqualTo(AiProvider.Protocol.MINIMAX_RESPONSES);
    }

    @Test
    void parsesResponse() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.MINIMAX_RESPONSES), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .block();

        assertThat(response).isNotNull();
        assertThat(response.message().parts())
                .hasSize(3)
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                                assertThat(thinking.text()).isEqualTo("think")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                                assertThat(text.text()).isEqualTo("Hello")))
                .anySatisfy(part -> assertThat(part)
                        .isInstanceOfSatisfying(AiContentPart.ToolCall.class, call ->
                                assertThat(call.id()).isEqualTo("call_1")));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(10, 5, 2, null));
    }

    @Test
    void postsResponsesRequestWithMultimodalInput() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.MINIMAX_RESPONSES), account(),
                        model("MiniMax-M3.1-Flash", 2048), request())
                .block();

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/v1/responses");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer mm-key");
        var body = body(httpRequest);
        assertThat(body)
                .contains("\"model\":\"MiniMax-M3.1-Flash\"")
                .contains("\"instructions\":\"You are helpful.\"")
                .contains("\"service_tier\":\"priority\"")
                .contains("\"reasoning\":{\"effort\":\"high\"}")
                .contains("\"type\":\"input_image\"")
                .contains("\"image_url\":\"mm_file://file-1\"")
                .contains("\"type\":\"input_video\"")
                .contains("\"video_url\":\"mm_file://file-2\"")
                .contains("\"type\":\"summary_text\"")
                .contains("\"type\":\"function_call\"")
                .contains("\"type\":\"function_call_output\"");
    }

    private AiChatIr request() {
        Map<String, JsonNode> extras = Map.of(
                "service_tier", mapper.getNodeFactory().textNode("priority"),
                "reasoning", mapper.createObjectNode().put("effort", "high"));
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
                        new AiChatMessage("user", List.of(
                                new AiContentPart.Text("describe", null),
                                new AiContentPart.File(
                                        "image/png", null, null, "a.png", "file-1", null),
                                new AiContentPart.File(
                                        "video/mp4", null, null, "v.mp4", "file-2", null))),
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
                List.of(new AiTool(
                        "get_weather",
                        "Weather lookup",
                        mapper.createObjectNode().put("type", "object"),
                        null)),
                new AiRequestParams("requested", 0.2, null, 0.9, null, List.of(), null, extras),
                false);
    }

    private MinimaxResponsesProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new MinimaxResponsesProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
