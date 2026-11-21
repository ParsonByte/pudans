package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.body;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeepSeekToolsClientTest {

    private static final String SEARCH_BODY = """
            {
              "results": [
                {"rerank_score": 2.4, "title": "DeepSeek V4", "url": "https://example.com",
                 "snippet": "short", "summary": "long", "content": "", "site_name": "Example",
                 "site_icon": "https://example.com/favicon.ico", "date": "2026-06-01T00:00:00+00:00",
                 "authority": 3}
              ]
            }
            """;

    private static final String FETCH_BODY = """
            {"title": "Example", "content": "# Hello"}
            """;

    private static final String OCR_BODY = """
            {
              "id": "ocr-1",
              "object": "ocr.result",
              "created": 1700000000,
              "purpose": "text_recognition",
              "page_count": 1,
              "pages": [{"index": 0, "text": "Hello", "finish_reason": "stop"}],
              "usage": {"input_tokens": 3, "output_tokens": 1, "total_tokens": 4}
            }
            """;

    private static final String ASR_BODY = """
            {"request_id": "asr-1", "text": "hello", "duration_ms": 3200}
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final ProviderAccounts providerAccounts = mock(ProviderAccounts.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void searchesWeb() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var result = client(json(SEARCH_BODY))
                .search(provider(AiProvider.Protocol.DEEPSEEK), account(),
                        new DeepSeekToolsClient.SearchRequest("deepseek", 5, "question", "1.2.3.4", true))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("DeepSeek V4");
        assertThat(result.results().get(0).rerankScore()).isEqualTo(2.4);
        assertThat(result.results().get(0).authority()).isEqualTo(3);

        var httpRequest = captured.get();
        assertThat(httpRequest.url().toString())
                .isEqualTo("https://api.example.com/v1/tools/search");
        assertThat(httpRequest.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer sk-ds-secret");
        var body = body(httpRequest);
        assertThat(body)
                .contains("\"query\":\"deepseek\"")
                .contains("\"count\":5")
                .contains("\"ori_question\":\"question\"")
                .contains("\"localization\":\"1.2.3.4\"")
                .contains("\"need_content\":true");
    }

    @Test
    void fetchesWebPage() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var result = client(json(FETCH_BODY))
                .fetch(provider(AiProvider.Protocol.DEEPSEEK), account(), "https://example.com")
                .block();

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Example");
        assertThat(result.content()).isEqualTo("# Hello");
        assertThat(captured.get().url().toString())
                .isEqualTo("https://api.example.com/v1/tools/fetch");
        assertThat(body(captured.get())).contains("\"url\":\"https://example.com\"");
    }

    @Test
    void recognizesDocument() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var result = client(json(OCR_BODY))
                .ocr(provider(AiProvider.Protocol.DEEPSEEK), account(), "report.pdf",
                        "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8), "text_recognition")
                .block();

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("ocr-1");
        assertThat(result.pages()).hasSize(1);
        assertThat(result.usage().totalTokens()).isEqualTo(4);

        var request = captured.get();
        assertThat(request.url().toString()).isEqualTo("https://api.example.com/v1/tools/ocr");
        var multipart = body(request);
        assertThat(multipart)
                .contains("name=\"purpose\"")
                .contains("text_recognition")
                .contains("filename=\"report.pdf\"");
    }

    @Test
    void recognizesAudio() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var result = client(json(ASR_BODY))
                .asr(provider(AiProvider.Protocol.DEEPSEEK), account(), "recording.wav",
                        "audio/wav", "wav".getBytes(StandardCharsets.UTF_8))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.durationMs()).isEqualTo(3200);
        assertThat(body(captured.get())).contains("filename=\"recording.wav\"");
    }

    @Test
    void streamsSpeechAudio() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));
        var response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .body("audio")
                .build();

        var chunks = client(response)
                .tts(provider(AiProvider.Protocol.DEEPSEEK), account(), "hello", "stella", "wav", true)
                .map(buffer -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .collectList()
                .block();

        assertThat(chunks).isNotEmpty();
        assertThat(new String(chunks.get(0), StandardCharsets.UTF_8)).isEqualTo("audio");
        assertThat(body(captured.get()))
                .contains("\"text\":\"hello\"")
                .contains("\"voice_id\":\"stella\"")
                .contains("\"format\":\"wav\"")
                .contains("\"filter_markdown\":true");
    }

    private DeepSeekToolsClient client(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new DeepSeekToolsClient(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, providerAccounts);
    }
}
