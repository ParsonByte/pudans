package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
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

class QianwenModelAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final ProviderAccounts providerAccounts = mock(ProviderAccounts.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void submitsSpeechRecognitionTasksAsynchronously() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"output":{"task_id":"task-asr","task_status":"PENDING"}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("qwen-audio-asr"),
                        new AiModelRequest(
                                AiModel.Capability.SPEECH_RECOGNITION,
                                List.of(new AiModelContent.Audio(
                                        "https://cdn.example.com/audio.wav", null, "audio/wav", 9, "wav", null)),
                                Map.of("language", mapper.getNodeFactory().textNode("zh")),
                                false,
                                null))
                .block();

        assertThat(response.pending()).isTrue();
        assertThat(response.providerTaskId()).isEqualTo("task-asr");
        var request = captured.get();
        assertThat(request.url().toString())
                .isEqualTo("https://maas.example.com/api/v1/services/audio/asr/transcription");
        assertThat(request.headers().getFirst("X-DashScope-Async")).isEqualTo("enable");
        var body = body(request);
        assertThat(body).contains("\"file_urls\":[\"https://cdn.example.com/audio.wav\"]");
        assertThat(body).contains("\"language\":\"zh\"");
    }

    @Test
    void pollsSpeechRecognitionTranscriptionFiles() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapterFor(request -> {
            captured.set(request);
            if (request.url().toString().contains("/api/v1/tasks/")) {
                return Mono.just(json("""
                        {"output":{"task_id":"task-asr","task_status":"SUCCEEDED","results":[
                          {"transcription_url":"https://cdn.example.com/transcript.json"}
                        ]},"usage":{"duration":9}}
                        """));
            }
            return Mono.just(json("""
                    {"transcripts":[{"text":"你好","content_duration_in_milliseconds":3000}]}
                    """));
        });

        var response = adapter.poll(
                        provider(),
                        account(),
                        model("qwen-audio-asr"),
                        AiModel.Capability.SPEECH_RECOGNITION,
                        "task-asr",
                        Map.of())
                .block();

        assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.SUCCEEDED);
        assertThat(response.outputs()).containsExactly(new AiModelContent.Text("你好"));
        assertThat(response.usage().audioSeconds()).isEqualTo(9);
    }

    @Test
    void generatesMusicThroughTheAudioEndpoint() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"output":{"audio":{"url":"https://cdn.example.com/music.mp3"}},
                 "usage":{"duration":120}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("fun-music-v1"),
                        new AiModelRequest(
                                AiModel.Capability.MUSIC_GENERATION,
                                List.of(new AiModelContent.Text("轻快的清晨音乐")),
                                Map.of("format", mapper.getNodeFactory().textNode("mp3")),
                                false,
                                null))
                .block();

        assertThat(response.outputs()).hasSize(1);
        var audio = (AiModelContent.Audio) response.outputs().getFirst();
        assertThat(audio.url()).isEqualTo("https://cdn.example.com/music.mp3");
        assertThat(audio.format()).isEqualTo("mp3");
        assertThat(response.usage().audioSeconds()).isEqualTo(120);
        assertThat(captured.get().url().toString())
                .isEqualTo("https://maas.example.com/api/v1/services/audio/music/generation");
        assertThat(body(captured.get())).contains("\"prompt\":\"轻快的清晨音乐\"");
    }

    @Test
    void generatesImagesThroughMultimodalGeneration() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"request_id":"req-1","output":{"choices":[
                  {"message":{"content":[{"image":"https://img.example.com/1.png"}]}}
                ]},"usage":{"output_image_count":1}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("qwen-image-3.0"),
                        new AiModelRequest(
                                AiModel.Capability.IMAGE_GENERATION,
                                List.of(new AiModelContent.Text("一只猫")),
                                Map.of("size", mapper.getNodeFactory().textNode("1024*1024")),
                                false,
                                null))
                .block();

        assertThat(response.outputs()).hasSize(1);
        assertThat(response.outputs().getFirst()).isInstanceOf(AiModelContent.Image.class);
        assertThat(((AiModelContent.Image) response.outputs().getFirst()).url())
                .isEqualTo("https://img.example.com/1.png");
        assertThat(response.usage().imageCount()).isEqualTo(1);

        var request = captured.get();
        assertThat(request.url().toString())
                .isEqualTo("https://maas.example.com/api/v1/services/aigc/multimodal-generation/generation");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
        var body = body(request);
        assertThat(body).contains("\"model\":\"qwen-image-3.0\"");
        assertThat(body).contains("\"text\":\"一只猫\"");
        assertThat(body).contains("\"parameters\":{\"size\":\"1024*1024\"}");
    }

    @Test
    void submitsVideoTasksAsynchronously() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"request_id":"req-2","output":{"task_id":"task-1","task_status":"PENDING"}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("wan3.0-video"),
                        new AiModelRequest(
                                AiModel.Capability.VIDEO_GENERATION,
                                List.of(
                                        new AiModelContent.Text("海浪"),
                                        new AiModelContent.Image("https://img.example.com/first.png", null, null, null, null)),
                                Map.of("duration", mapper.getNodeFactory().numberNode(5)),
                                false,
                                null))
                .block();

        assertThat(response.pending()).isTrue();
        assertThat(response.providerTaskId()).isEqualTo("task-1");

        var request = captured.get();
        assertThat(request.url().toString())
                .isEqualTo("https://maas.example.com/api/v1/services/aigc/video-generation/video-synthesis");
        assertThat(request.headers().getFirst("X-DashScope-Async")).isEqualTo("enable");
        var body = body(request);
        assertThat(body).contains("\"prompt\":\"海浪\"");
        assertThat(body).contains("\"img_url\":\"https://img.example.com/first.png\"");
        assertThat(body).contains("\"duration\":5");
    }

    @Test
    void submitsThreeDTasksWithSingleImage() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"output":{"task_id":"task-3d","task_status":"PENDING"}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("Tripo/Tripo-P1.0"),
                        new AiModelRequest(
                                AiModel.Capability.THREE_D_GENERATION,
                                List.of(
                                        new AiModelContent.Text("一只猫"),
                                        new AiModelContent.Image("https://img.example.com/cat.png", null, "image/png", null, null)),
                                Map.of(),
                                false,
                                null))
                .block();

        assertThat(response.pending()).isTrue();
        assertThat(response.providerTaskId()).isEqualTo("task-3d");
        assertThat(captured.get().url().toString())
                .isEqualTo("https://maas.example.com/api/v1/services/aigc/video-generation/3d-generation");
        var body = body(captured.get());
        assertThat(body).contains("\"prompt\":\"一只猫\"");
        assertThat(body).contains("\"image\":\"https://img.example.com/cat.png\"");
    }

    @Test
    void padsThreeDMultiImageInputsToFourViews() throws Exception {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"output":{"task_id":"task-3d","task_status":"PENDING"}}
                """));

        adapter.invoke(
                        provider(),
                        account(),
                        model("Tripo/Tripo-P1.0"),
                        new AiModelRequest(
                                AiModel.Capability.THREE_D_GENERATION,
                                List.of(
                                        new AiModelContent.Image("https://img/cat-1.png", null, "image/png", null, null),
                                        new AiModelContent.Image("https://img/cat-2.png", null, "image/png", null, null)),
                                Map.of(),
                                false,
                                null))
                .block();

        var images = mapper.readTree(body(captured.get())).path("input").path("images");
        assertThat(images).hasSize(4);
        assertThat(images.get(0).path("file_token").asText()).isEqualTo("https://img/cat-1.png");
        assertThat(images.get(1).path("file_token").asText()).isEqualTo("https://img/cat-2.png");
        assertThat(images.get(2).isObject()).isTrue();
        assertThat(images.get(2).size()).isZero();
        assertThat(images.get(3).isObject()).isTrue();
        assertThat(images.get(3).size()).isZero();
    }

    @Test
    void pollsThreeDTasksAndReturnsModelUrl() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"output":{"task_id":"task-3d","task_status":"SUCCEEDED","results":[
                  {"model_url":"https://cdn.example.com/model.glb",
                   "pbr_model_url":"https://cdn.example.com/model-pbr.glb"}
                ]},"usage":{"3d_task_type":"text-to-3d","credits_consumed":40}}
                """));

        var response = adapter.poll(
                        provider(),
                        account(),
                        model("Tripo/Tripo-P1.0"),
                        AiModel.Capability.THREE_D_GENERATION,
                        "task-3d",
                        Map.of())
                .block();

        assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.SUCCEEDED);
        assertThat(response.outputs()).hasSize(1);
        assertThat(response.outputs().getFirst()).isInstanceOf(AiModelContent.ThreeD.class);
        assertThat(((AiModelContent.ThreeD) response.outputs().getFirst()).url())
                .isEqualTo("https://cdn.example.com/model-pbr.glb");
        assertThat(response.usage().taskCount()).isEqualTo(1);
        assertThat(captured.get().url().toString())
                .isEqualTo("https://maas.example.com/api/v1/tasks/task-3d");
    }

    @Test
    void reportsFailedTasks() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"output":{"task_id":"task-x","task_status":"FAILED","message":"参数错误"}}
                """));

        var response = adapter.poll(
                        provider(),
                        account(),
                        model("wan3.0-video"),
                        AiModel.Capability.VIDEO_GENERATION,
                        "task-x",
                        Map.of())
                .block();

        assertThat(response.status()).isEqualTo(AiModelResponse.TaskStatus.FAILED);
        assertThat(response.finishReason()).isEqualTo("参数错误");
    }

    @Test
    void understandsVideosWithTextOutput() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"request_id":"req-v","output":{"choices":[
                  {"message":{"content":[{"text":"视频里有一只猫"}]}}
                ]},"usage":{"input_tokens":10,"output_tokens":5}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("qwen3-vl-plus"),
                        new AiModelRequest(
                                AiModel.Capability.VIDEO_UNDERSTANDING,
                                List.of(
                                        new AiModelContent.Text("描述视频"),
                                        new AiModelContent.Video("https://cdn.example.com/cat.mp4", null, null, null, null)),
                                Map.of(),
                                false,
                                null))
                .block();

        assertThat(response.outputs()).hasSize(1);
        assertThat(response.outputs().getFirst()).isEqualTo(new AiModelContent.Text("视频里有一只猫"));
        assertThat(response.usage().inputTokens()).isEqualTo(10);
        assertThat(response.usage().outputTokens()).isEqualTo(5);
        assertThat(response.usage().imageCount()).isNull();
        var body = body(captured.get());
        assertThat(body).contains("\"text\":\"描述视频\"");
        assertThat(body).contains("\"video\":\"https://cdn.example.com/cat.mp4\"");
    }

    @Test
    void synthesizesSpeechWithTheAudioEndpoint() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter(json("""
                {"request_id":"req-s","output":{"audio":{
                  "url":"https://cdn.example.com/speech.wav","duration":1.2
                }}}
                """));

        var response = adapter.invoke(
                        provider(),
                        account(),
                        model("qwen-audio-3.1-tts-next"),
                        new AiModelRequest(
                                AiModel.Capability.SPEECH_SYNTHESIS,
                                List.of(new AiModelContent.Text("你好")),
                                Map.of("format", mapper.getNodeFactory().textNode("wav")),
                                false,
                                null))
                .block();

        assertThat(response.outputs()).hasSize(1);
        var audio = (AiModelContent.Audio) response.outputs().getFirst();
        assertThat(audio.url()).isEqualTo("https://cdn.example.com/speech.wav");
        assertThat(audio.durationSeconds()).isEqualTo(2);
        assertThat(audio.format()).isEqualTo("wav");
        assertThat(response.usage().speechCharacters()).isEqualTo(2);
        assertThat(adapter.capabilities()).contains(AiModel.Capability.AUDIO_GENERATION);
        var body = body(captured.get());
        assertThat(captured.get().url().toString())
                .isEqualTo("https://maas.example.com/api/v1/services/audio/tts/SpeechSynthesizer");
        assertThat(body).contains("\"text_prompt\":\"你好\"");
        assertThat(body).contains("\"format\":\"wav\"");
    }

    private QianwenModelAdapter adapter(ClientResponse response) {
        return adapterFor(request -> {
            captured.set(request);
            return Mono.just(response);
        });
    }

    private QianwenModelAdapter adapterFor(ExchangeFunction exchange) {
        var webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new QianwenModelAdapter(
                new QianwenApiClient(webClient, mapper, providerAccounts), mapper);
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(AiProvider.Protocol.QIANWEN);
        provider.getSpec().setBaseUrl("https://maas.example.com");
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
}
