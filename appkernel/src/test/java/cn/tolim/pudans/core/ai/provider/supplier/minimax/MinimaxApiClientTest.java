package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
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
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

class MinimaxApiClientTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void postsJsonWithBearerAndApiKey() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var node = client(json("{\"lyrics\":\"hello\",\"base_resp\":{\"status_code\":0}}"))
                .post(provider(AiProvider.Protocol.MINIMAX), account(),
                        "/v1/lyrics_generation", mapper.createObjectNode().put("prompt", "hello"))
                .block();

        assertThat(node).isNotNull();
        assertThat(node.get("lyrics").asText()).isEqualTo("hello");
        var request = captured.get();
        assertThat(request.url().toString())
                .isEqualTo("https://api.example.com/v1/lyrics_generation");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer mm-key");
        assertThat(request.headers().getFirst("x-api-key")).isEqualTo("mm-key");
        assertThat(request.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(body(request)).contains("\"prompt\":\"hello\"");
    }

    @Test
    void rejectsBaseRespErrors() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        assertThatThrownBy(() -> client(json(
                        "{\"base_resp\":{\"status_code\":1008,\"status_msg\":\"insufficient balance\"}}"))
                        .post(provider(AiProvider.Protocol.MINIMAX), account(),
                                "/v1/lyrics_generation", mapper.createObjectNode())
                        .block())
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(error.getReason()).contains("insufficient balance");
                });
    }

    @Test
    void getsWithQueryParameters() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        client(json("{\"files\":[]}"))
                .get(provider(AiProvider.Protocol.MINIMAX), account(),
                        "/v1/files/list", Map.of("purpose", "video_generation"))
                .block();

        assertThat(captured.get().url().toString())
                .isEqualTo("https://api.example.com/v1/files/list?purpose=video_generation");
    }

    @Test
    void uploadsMultipartFile() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        client(json("{\"file\":{\"file_id\":1},\"base_resp\":{\"status_code\":0}}"))
                .upload(provider(AiProvider.Protocol.MINIMAX), account(),
                        "/v1/files/upload", Map.of("purpose", "video_generation"), Map.of(),
                        "clip.mp4", "video/mp4", "video".getBytes(StandardCharsets.UTF_8))
                .block();

        var request = captured.get();
        assertThat(request.url().toString()).isEqualTo("https://api.example.com/v1/files/upload");
        assertThat(body(request))
                .contains("name=\"purpose\"")
                .contains("video_generation")
                .contains("filename=\"clip.mp4\"")
                .contains("video");
    }

    @Test
    void streamsJsonEvents() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));

        var events = client(sse("{\"delta\":\"a\"}", "{\"delta\":\"b\"}", "[DONE]"))
                .postStream(provider(AiProvider.Protocol.MINIMAX), account(),
                        "/v1/images/generations", mapper.createObjectNode().put("stream", true))
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("delta").asText()).isEqualTo("a");
    }

    @Test
    void downloadsBinaryContent() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("mm-key"));
        var response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body("binary")
                .build();

        var chunks = client(response)
                .content(provider(AiProvider.Protocol.MINIMAX), account(),
                        "/v1/files/retrieve_content", Map.of("file_id", "1"))
                .map(buffer -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .collectList()
                .block();

        assertThat(chunks).isNotEmpty();
        assertThat(new String(chunks.get(0), StandardCharsets.UTF_8)).isEqualTo("binary");
        assertThat(captured.get().url().toString())
                .isEqualTo("https://api.example.com/v1/files/retrieve_content?file_id=1");
    }

    private MinimaxApiClient client(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        return new MinimaxApiClient(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }
}
