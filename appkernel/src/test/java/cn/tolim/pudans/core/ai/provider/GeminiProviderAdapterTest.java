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
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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

class GeminiProviderAdapterTest {

    private static final String RESPONSE_BODY = """
            {
              "responseId": "resp_1",
              "modelVersion": "gemini-2.5-pro",
              "candidates": [{
                "content": {"role": "model", "parts": [
                  {"text": "Let me think", "thought": true},
                  {"text": "Hello"},
                  {"functionCall": {"name": "get_weather", "args": {"city": "Paris"}}}
                ]},
                "finishReason": "STOP"
              }],
              "usageMetadata": {"promptTokenCount": 12, "candidatesTokenCount": 7,
                                "cachedContentTokenCount": 3}
            }
            """;

    private static final String TEXT_CHUNK = """
            {"candidates":[{"content":{"parts":[{"text":"Hel"}]}}]}
            """;

    private static final String THOUGHT_CHUNK = """
            {"candidates":[{"content":{"parts":[{"text":"hmm","thought":true}]}}]}
            """;

    private static final String FUNCTION_CHUNK = """
            {"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"get_weather","args":{"city":"Paris"}}}]}}]}
            """;

    private static final String FINISH_CHUNK = """
            {"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}],
            "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2}}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void exposesProtocol() {
        assertThat(adapter(json("{}")).protocol()).isEqualTo(AiProvider.Protocol.GEMINI);
    }

    @Test
    void buildsHeadersWithGoogleApiKeyAndStreamAccept() {
        var provider = provider(AiProvider.Protocol.GEMINI);
        provider.getSpec().getHeaders().put("X-Org-Id", "org-1");

        var headers = adapter(json("{}")).buildHeaders(provider, "gemini-secret", true);

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(headers.getFirst("x-goog-api-key")).isEqualTo("gemini-secret");
        assertThat(headers.getFirst("X-Org-Id")).isEqualTo("org-1");
    }

    @Test
    void parsesNonStreamingResponse() {
        var response = adapter(json("{}")).parseChatResponse(RESPONSE_BODY);

        assertThat(response.id()).isEqualTo("resp_1");
        assertThat(response.model()).isEqualTo("gemini-2.5-pro");
        assertThat(response.finishReason()).isEqualTo("STOP");
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
                            assertThat(call.name()).isEqualTo("get_weather");
                            assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                        }));
        assertThat(response.usage()).isEqualTo(new AiTokenUsage(12, 7, 3, null));
    }

    @Test
    void parsesStreamEvents() {
        var adapter = adapter(json("{}"));

        assertThat(adapter.parseStreamEvent(TEXT_CHUNK).orElseThrow().content()).isEqualTo("Hel");
        assertThat(adapter.parseStreamEvent(THOUGHT_CHUNK).orElseThrow().reasoning()).isEqualTo("hmm");

        var toolCall = adapter.parseStreamEvent(FUNCTION_CHUNK).orElseThrow().toolCall();
        assertThat(toolCall.index()).isZero();
        assertThat(toolCall.name()).isEqualTo("get_weather");
        assertThat(toolCall.arguments()).isEqualTo("{\"city\":\"Paris\"}");

        var finish = adapter.parseStreamEvent(FINISH_CHUNK).orElseThrow();
        assertThat(finish.finishReason()).isEqualTo("STOP");
        assertThat(finish.usage()).isEqualTo(new AiTokenUsage(5, 2, null, null));

        assertThat(adapter.parseStreamEvent("{\"candidates\":[]}")).isEmpty();
        assertThat(adapter.parseStreamEvent(" ")).isEmpty();
    }

    @Test
    void postsGenerateContentRequest() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("gemini-secret"));

        var response = adapter(json(RESPONSE_BODY))
                .chat(provider(AiProvider.Protocol.GEMINI), account(),
                        model("gemini-2.5-pro", 2048), toolRequest(mapper))
                .block();

        assertThat(response).isNotNull();
        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/v1beta/models/gemini-2.5-pro:generateContent");
        assertThat(httpRequest.headers().getFirst("x-goog-api-key")).isEqualTo("gemini-secret");
        var body = body(httpRequest);
        assertThat(body).contains("\"systemInstruction\"");
        assertThat(body).contains("\"You are helpful.\"");
        assertThat(body).contains("\"role\":\"user\"");
        assertThat(body).contains("\"role\":\"model\"");
        assertThat(body).contains("\"functionCall\"");
        assertThat(body).contains("\"functionResponse\"");
        assertThat(body).contains("\"functionDeclarations\"");
        assertThat(body).contains("\"name\":\"get_weather\"");
        assertThat(body).contains("\"description\":\"Weather lookup\"");
        assertThat(body).contains("\"maxOutputTokens\":2048");
        assertThat(body).contains("\"temperature\":0.2");
        assertThat(body).doesNotContain("\"stream\"");
    }

    @Test
    void streamsSseWithAltQuery() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("gemini-secret"));

        var events = adapter(sse(TEXT_CHUNK, FINISH_CHUNK))
                .streamChat(provider(AiProvider.Protocol.GEMINI), account(),
                        model("gemini-2.5-pro", null), request())
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).content()).isEqualTo("Hel");
        assertThat(events.get(1).finishReason()).isEqualTo("STOP");

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString()).isEqualTo(
                "https://api.example.com/v1beta/models/gemini-2.5-pro"
                        + ":streamGenerateContent?alt=sse");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.ACCEPT))
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void rejectsResponseWithoutCandidates() {
        assertThatThrownBy(() -> adapter(json("{}")).parseChatResponse("{\"candidates\":[]}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("candidates");
    }

    @Test
    void rejectsInvalidBaseUrlWithConfigCode() {
        var provider = provider(AiProvider.Protocol.GEMINI);
        provider.getSpec().setBaseUrl("ftp://api.example.com");

        assertThatThrownBy(() -> adapter(json("{}"))
                        .chat(provider, account(), model("gemini-2.5-pro", null), request())
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(AiProviderConfigSupport.CODE_INVALID);
                });
    }

    @Test
    void rejectsRequestWithoutModelCode() {
        var model = model("gemini-2.5-pro", null);
        model.getSpec().setModelCode(null);
        var chatRequest = new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams(null, null, null, null, null, List.of(), null, Map.of()),
                false);

        assertThatThrownBy(() -> adapter(json("{}"))
                        .chat(provider(AiProvider.Protocol.GEMINI), account(), model, chatRequest)
                        .block())
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("模型编码");
    }

    private GeminiProviderAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new GeminiProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
