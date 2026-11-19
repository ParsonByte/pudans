package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
import reactor.test.StepVerifier;

class GeminiModelAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void generatesImagesFromInlineData() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("goog-key"));
        var adapter = adapter(json("""
                {"candidates":[{"content":{"parts":[
                  {"text":"这里是图片"},
                  {"inlineData":{"mimeType":"image/png","data":"aW1hZ2U="}}
                ]}}]}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("gemini-2.5-flash-image"),
                        new AiModelRequest(
                                AiModel.Capability.IMAGE_GENERATION,
                                List.of(new AiModelContent.Text("画一只猫")),
                                Map.of(),
                                false,
                                null))
                .block();

        assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.SUCCEEDED);
        assertThat(response.outputs()).hasSize(1);
        var image = (AiModelContent.Image) response.outputs().getFirst();
        assertThat(image.data()).isEqualTo("aW1hZ2U=");
        assertThat(image.mediaType()).isEqualTo("image/png");

        var request = captured.get();
        assertThat(request.url().toString()).isEqualTo(
                "https://generativelanguage.example.com/v1beta/models/gemini-2.5-flash-image:generateContent");
        assertThat(request.headers().getFirst("x-goog-api-key")).isEqualTo("goog-key");
        var body = body(request);
        assertThat(body).contains("\"text\":\"画一只猫\"");
        assertThat(body).contains("\"responseModalities\":[\"TEXT\",\"IMAGE\"]");
    }

    @Test
    void submitsLongRunningVideoOperations() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("goog-key"));
        var adapter = adapter(json("""
                {"name":"operations/veo-1"}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("veo-3.1-fast"),
                        new AiModelRequest(
                                AiModel.Capability.VIDEO_GENERATION,
                                List.of(new AiModelContent.Text("海浪")),
                                Map.of(),
                                false,
                                null))
                .block();

        assertThat(response.pending()).isTrue();
        assertThat(response.providerTaskId()).isEqualTo("operations/veo-1");
        assertThat(captured.get().url().toString()).isEqualTo(
                "https://generativelanguage.example.com/v1beta/models/veo-3.1-fast:predictLongRunning");
        assertThat(body(captured.get())).contains("\"prompt\":\"海浪\"");
    }

    @Test
    void pollsCompletedVideoOperations() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("goog-key"));
        var adapter = adapter(json("""
                {"name":"operations/veo-1","done":true,"response":{"generateVideoResponse":{
                  "generatedSamples":[{"video":{"uri":"https://cdn.example.com/video.mp4"}}]
                }}}
                """));

        var response = adapter.poll(
                        provider(),
                        account(),
                        model("veo-3.1-fast"),
                        AiModel.Capability.VIDEO_GENERATION,
                        "operations/veo-1",
                        Map.of())
                .block();

        assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.SUCCEEDED);
        assertThat(response.outputs()).hasSize(1);
        assertThat(((AiModelContent.Video) response.outputs().getFirst()).url())
                .isEqualTo("https://cdn.example.com/video.mp4");
        assertThat(captured.get().url().toString())
                .isEqualTo("https://generativelanguage.example.com/v1beta/operations/veo-1");
    }

    @Test
    void keepsPollingUntilTheOperationCompletes() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("goog-key"));
        var adapter = adapter(json("""
                {"name":"operations/veo-1","done":false}
                """));

        var response = adapter.poll(
                        provider(),
                        account(),
                        model("veo-3.1-fast"),
                        AiModel.Capability.VIDEO_GENERATION,
                        "operations/veo-1",
                        Map.of())
                .block();

        assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.RUNNING);
        assertThat(response.providerTaskId()).isEqualTo("operations/veo-1");
    }

    private GeminiModelAdapter adapter(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(response);
        };
        var webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new GeminiModelAdapter(
                new GeminiMediaClient(webClient, mapper, accountService), mapper);
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(AiProvider.Protocol.GEMINI);
        provider.getSpec().setBaseUrl("https://generativelanguage.example.com");
        return provider;
    }

    private static AiProviderAccount account() {
        return new AiProviderAccount();
    }

    private static AiModel model(String modelCode) {
        var model = new AiModel();
        model.getSpec().setModelCode(modelCode);
        return model;
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }

    @Test
    void reportsFailedOperations() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("goog-key"));
        var adapter = adapter(json("""
                {"name":"operations/veo-1","done":true,"error":{"message":"quota exceeded"}}
                """));

        StepVerifier.create(adapter.poll(
                        provider(),
                        account(),
                        model("veo-3.1-fast"),
                        AiModel.Capability.VIDEO_GENERATION,
                        "operations/veo-1",
                        Map.of()))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.FAILED);
                    assertThat(response.finishReason()).isEqualTo("quota exceeded");
                })
                .verifyComplete();
    }
}
