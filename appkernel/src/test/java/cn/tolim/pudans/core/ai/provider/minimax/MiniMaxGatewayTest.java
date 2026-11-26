package cn.tolim.pudans.core.ai.provider.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiImageGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageSubjectReference;
import cn.tolim.pudans.contract.ai.dto.AiLegacyImageRequest;
import cn.tolim.pudans.contract.ai.dto.AiLegacyVideoRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageInput;
import cn.tolim.pudans.contract.ai.dto.AiLyricsGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiMusicCoverRequest;
import cn.tolim.pudans.contract.ai.dto.AiMusicGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechAsyncRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentInput;
import cn.tolim.pudans.contract.ai.dto.AiVideoAgentRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoContent;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoRegenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceClonePrompt;
import cn.tolim.pudans.contract.ai.dto.AiVoiceCloneRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDeleteRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceDesignRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListRequest;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiTranscriptionOptions;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MiniMaxGatewayTest {

    private static final Map<String, String> CREDENTIALS = Map.of("apiKey", "secret");

    private static final String BASE = "https://api.minimax.cn";

    @Mock
    AiProviderHttp http;

    private MiniMaxGateway gateway;

    private AiChannelEntity channel;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var provider = new MiniMaxProvider(http);
        gateway = new MiniMaxGateway(http, provider);
        channel = channel();
    }

    @Test
    void uploadsAndMapsTheMiniMaxFileObject() throws Exception {
        var file = Files.writeString(tempDir.resolve("voice.mp3"), "voice");
        when(http.postMultipart(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "file": {
                            "file_id": 184017753380191,
                            "bytes": 5,
                            "created_at": 1700469398,
                            "filename": "voice.mp3",
                            "purpose": "voice_clone"
                          },
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        StepVerifier.create(gateway.uploadFile(
                        channel, CREDENTIALS, "voice_clone", "voice.mp3", "audio/mpeg", file, null))
                .assertNext(uploaded -> {
                    assertThat(uploaded.id()).isEqualTo("184017753380191");
                    assertThat(uploaded.object()).isEqualTo("file");
                    assertThat(uploaded.bytes()).isEqualTo(5);
                    assertThat(uploaded.createdAt()).isEqualTo(1700469398L);
                    assertThat(uploaded.filename()).isEqualTo("voice.mp3");
                    assertThat(uploaded.purpose()).isEqualTo("voice_clone");
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, Object>> parts =
                ArgumentCaptor.forClass(MultiValueMap.class);
        verify(http).postMultipart(
                eq(URI.create(BASE + "/v1/files/upload")),
                eq(CREDENTIALS),
                eq(Map.of()),
                parts.capture(),
                any(Duration.class));
        assertThat(parts.getValue().getFirst("purpose")).isEqualTo("voice_clone");
        assertThat(parts.getValue().getFirst("file")).isNotNull();
    }

    @Test
    void listsFilesAndFlattensTheProviderEnvelope() {
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "files": [
                            {"file_id": 1, "bytes": 10, "created_at": 1700000000,
                             "filename": "a.tar", "purpose": "t2a_async_input"},
                            {"file_id": 2, "bytes": 20, "created_at": 1700000001,
                             "filename": "b.tar", "purpose": "t2a_async_input"}
                          ],
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        StepVerifier.create(gateway.listFiles(
                        channel, CREDENTIALS, null, null, null, "t2a_async_input"))
                .assertNext(page -> {
                    assertThat(page.object()).isEqualTo("list");
                    assertThat(page.data()).extracting(file -> file.id()).containsExactly("1", "2");
                    assertThat(page.hasMore()).isFalse();
                })
                .verifyComplete();

        verify(http).getJson(
                eq(URI.create(BASE + "/v1/files/list?purpose=t2a_async_input")),
                eq(CREDENTIALS),
                eq(Map.of()),
                any(Duration.class));
    }

    @Test
    void deletesAFileWithThePurposeReturnedByRetrieve() {
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "file": {"file_id": 42, "filename": "clip.mp4", "purpose": "video_generation_input"},
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"file_id": 42, "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """));

        StepVerifier.create(gateway.deleteFile(channel, CREDENTIALS, "42"))
                .assertNext(deletion -> {
                    assertThat(deletion.id()).isEqualTo("42");
                    assertThat(deletion.deleted()).isTrue();
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(http).postJson(
                eq(URI.create(BASE + "/v1/files/delete")),
                eq(CREDENTIALS),
                eq(Map.of()),
                body.capture(),
                any(Duration.class));
        assertThat(body.getValue()).containsEntry("file_id", 42L);
        assertThat(body.getValue()).containsEntry("purpose", "video_generation_input");
    }

    @Test
    void surfacesBusinessErrorsReturnedWithHttpOk() {
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "file": {"file_id": 42, "filename": "clip.mp4", "purpose": "video_generation_input"},
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"base_resp": {"status_code": 1004, "status_msg": "authentication failed"}}
                        """));

        StepVerifier.create(gateway.deleteFile(channel, CREDENTIALS, "42"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo("ai-provider-request-failed");
                    assertThat(api.getReason()).contains("authentication failed").contains("1004");
                })
                .verify();
    }

    @Test
    void synthesizesHexAudioWithVoiceAndAudioSettings() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "data": {"audio": "77617665", "status": 2},
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        var request = new AiSpeechRequest(
                "Hello",
                "male-qn-qingse",
                "wav",
                null,
                "speech-2.8-hd",
                1.2,
                1.0,
                0,
                "happy",
                32000,
                128000,
                1,
                null,
                false,
                null);

        var speech = gateway.synthesize(channel, CREDENTIALS, request).block();
        assertThat(speech).isNotNull();
        assertThat(speech.contentType()).isEqualTo("audio/wav");
        assertThat(text(speech.audio())).isEqualTo("wave");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(http).postJson(
                eq(URI.create(BASE + "/v1/t2a_v2")),
                eq(CREDENTIALS),
                eq(Map.of()),
                body.capture(),
                any(Duration.class));
        @SuppressWarnings("unchecked")
        var values = (Map<String, Object>) body.getValue();
        assertThat(values).containsEntry("model", "speech-2.8-hd");
        assertThat(values).containsEntry("text", "Hello");
        assertThat(values).containsEntry("stream", false);
        assertThat((Map<String, Object>) values.get("voice_setting"))
                .containsEntry("voice_id", "male-qn-qingse")
                .containsEntry("speed", 1.2)
                .containsEntry("vol", 1.0)
                .containsEntry("pitch", 0)
                .containsEntry("emotion", "happy");
        assertThat((Map<String, Object>) values.get("audio_setting"))
                .containsEntry("format", "wav")
                .containsEntry("sample_rate", 32000)
                .containsEntry("bitrate", 128000)
                .containsEntry("channel", 1);
    }

    @Test
    void decodesStreamingSpeechChunks() {
        var first = "{\"data\":{\"audio\":\"7761\",\"status\":1},"
                + "\"base_resp\":{\"status_code\":0}}";
        var last = "{\"data\":{\"audio\":\"7665\",\"status\":2},"
                + "\"base_resp\":{\"status_code\":0}}";
        when(http.postSse(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Flux.just(
                        ServerSentEvent.builder(first).build(),
                        ServerSentEvent.builder(last).build()));

        var request = new AiSpeechRequest(
                "Hello", "male-qn-qingse", "pcm", null, "speech-2.8-hd", null, null, null, null,
                null, null, null, null, true, null);
        var speech = gateway.synthesize(channel, CREDENTIALS, request).block();

        assertThat(speech).isNotNull();
        assertThat(speech.contentType()).isEqualTo("audio/pcm");
        assertThat(text(speech.audio())).isEqualTo("wave");
    }

    @Test
    void transcribesVerboseJsonWithSegments() throws Exception {
        var audio = Files.writeString(tempDir.resolve("meeting.wav"), "audio");
        when(http.postMultipart(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "text": "hello world",
                          "duration": 26.325,
                          "n_speakers": 2,
                          "segments": [
                            {"id": 0, "start": 0.1, "end": 1.66, "speaker": "S1", "text": "hello"},
                            {"id": 1, "start": 2.0, "end": 6.1, "speaker": "S2", "text": "world"}
                          ],
                          "trace_id": "trace-1"
                        }
                        """));

        StepVerifier.create(gateway.transcribe(
                        channel,
                        CREDENTIALS,
                        "meeting.wav",
                        "audio/wav",
                        audio,
                        new AiTranscriptionOptions("asr-1.0", "en", "verbose_json")))
                .assertNext(result -> {
                    assertThat(result.requestId()).isEqualTo("trace-1");
                    assertThat(result.text()).isEqualTo("hello world");
                    assertThat(result.durationMs()).isEqualTo(26325);
                    assertThat(result.speakers()).isEqualTo(2);
                    assertThat(result.segments()).hasSize(2);
                    assertThat(result.segments().getFirst().speaker()).isEqualTo("S1");
                })
                .verifyComplete();

        verify(http).postMultipart(
                eq(URI.create(BASE + "/v1/speech_to_text")),
                eq(CREDENTIALS),
                eq(Map.of("language", "en")),
                any(),
                any(Duration.class));
    }

    @Test
    void rejectsUnsupportedTranscriptionFormatsBeforeCallingTheProvider() throws Exception {
        var audio = Files.writeString(tempDir.resolve("meeting.wav"), "audio");

        StepVerifier.create(gateway.transcribe(
                        channel,
                        CREDENTIALS,
                        "meeting.wav",
                        "audio/wav",
                        audio,
                        new AiTranscriptionOptions(null, null, "xml")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-provider-request-invalid");
                })
                .verify();

        verify(http, never()).postMultipart(any(), any(), any(), any(), any(Duration.class));
    }

    @Test
    void proxiesRawProtocolBodies() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("{\"id\":\"chat-1\"}"));
        when(http.postJson(any(), any(), any(), any(), any(Duration.class), any()))
                .thenReturn(Mono.just("{\"type\":\"message\"}"));

        StepVerifier.create(gateway.chatCompletions(
                        channel, CREDENTIALS, Map.of("model", "MiniMax-M3")))
                .expectNext("{\"id\":\"chat-1\"}")
                .verifyComplete();
        StepVerifier.create(gateway.messages(
                        channel, CREDENTIALS, Map.of("model", "MiniMax-M3"), null, true))
                .expectNext("{\"type\":\"message\"}")
                .verifyComplete();
        StepVerifier.create(gateway.responses(
                        channel, CREDENTIALS, Map.of("model", "MiniMax-M3")))
                .expectNext("{\"id\":\"chat-1\"}")
                .verifyComplete();

        verify(http).postJson(
                eq(URI.create(BASE + "/v1/chat/completions")),
                eq(CREDENTIALS),
                eq(Map.of()),
                eq(Map.of("model", "MiniMax-M3")),
                any(Duration.class));
        verify(http).postJson(
                eq(URI.create(BASE + "/anthropic/v1/messages")),
                eq(CREDENTIALS),
                eq(Map.of("anthropic-version", "2023-06-01")),
                eq(Map.of("model", "MiniMax-M3")),
                any(Duration.class),
                any());
        verify(http).postJson(
                eq(URI.create(BASE + "/v1/responses")),
                eq(CREDENTIALS),
                eq(Map.of()),
                eq(Map.of("model", "MiniMax-M3")),
                any(Duration.class));
    }

    @Test
    void readsVideoTasksFromNestedAndFlatResponses() {
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "task": {
                            "id": "424010985738629",
                            "model": "MiniMax-H3",
                            "status": "succeeded",
                            "content": {"url": "https://cdn/output.mp4"},
                            "resolution": "2K",
                            "duration": 5,
                            "ratio": "16:9",
                            "task_type": "generation"
                          }
                        }
                        """))
                .thenReturn(Mono.just("""
                        {
                          "items": [
                            {"id": "1", "model": "MiniMax-H3", "status": "queued"},
                            {"id": "2", "model": "MiniMax-H3", "status": "running"}
                          ],
                          "total": 2
                        }
                        """));

        StepVerifier.create(gateway.videoTask(channel, CREDENTIALS, "424010985738629"))
                .assertNext(task -> {
                    assertThat(task.id()).isEqualTo("424010985738629");
                    assertThat(task.status()).isEqualTo("succeeded");
                    assertThat(task.content().url()).isEqualTo("https://cdn/output.mp4");
                })
                .verifyComplete();
        StepVerifier.create(gateway.videoTasks(
                        channel, CREDENTIALS, 1, 20, "queued", List.of("1"), "MiniMax-H3", "generation"))
                .assertNext(page -> {
                    assertThat(page.total()).isEqualTo(2);
                    assertThat(page.items()).extracting(task -> task.id()).containsExactly("1", "2");
                })
                .verifyComplete();
    }

    @Test
    void generatesImagesAndRejectsInvalidQuantities() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "created": 1792308678,
                          "data": [
                            {"url": "https://example.com/output-0.jpg", "size": "2720x1536",
                             "revised_prompt": "a cat"}
                          ],
                          "output_format": "jpeg",
                          "usage": {"generated_images": 1, "total_tokens": 7254},
                          "request_id": "request-1"
                        }
                        """));

        var request = new AiImageGenerationRequest(
                "MiniMax-H3-Image", "a cat", null, 1, "2720x1536", null, null, "jpeg", "url",
                false, null, null, List.of());
        StepVerifier.create(gateway.imageGenerations(channel, CREDENTIALS, request))
                .assertNext(result -> {
                    assertThat(result.requestId()).isEqualTo("request-1");
                    assertThat(result.data()).hasSize(1);
                    assertThat(result.data().getFirst().url())
                            .isEqualTo("https://example.com/output-0.jpg");
                    assertThat(result.usage().generatedImages()).isEqualTo(1);
                })
                .verifyComplete();

        var invalid = new AiImageGenerationRequest(
                "MiniMax-H3-Image", "a cat", null, 11, null, null, null, null, null,
                null, null, null, List.of());
        StepVerifier.create(gateway.imageGenerations(channel, CREDENTIALS, invalid))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("1 到 10"))
                .verify();
    }

    @Test
    void editsImagesAndRequiresReferenceImages() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"created": 1, "data": [{"url": "https://example.com/edit.jpg"}], "output_format": "jpeg"}
                        """));

        var request = new AiImageGenerationRequest(
                "MiniMax-H3-Image",
                "edit it",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new AiImageInput("https://example.com/input.jpg")));
        StepVerifier.create(gateway.imageEdits(channel, CREDENTIALS, request))
                .assertNext(result -> assertThat(result.data()).hasSize(1))
                .verifyComplete();

        var missing = new AiImageGenerationRequest(
                "MiniMax-H3-Image", "edit it", null, null, null, null, null, null, null,
                null, null, null, List.of());
        StepVerifier.create(gateway.imageEdits(channel, CREDENTIALS, missing))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("至少需要一张参考图"))
                .verify();
    }

    @Test
    void createsVideoGenerationContextAndRegenerationTasks() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("{\"task_id\":\"task-1\"}"));

        var content = List.of(new AiVideoContent("text", "a cat dives", null, null, null, null));
        var generation = new AiVideoGenerationRequest(
                "MiniMax-H3", content, "768P", 5, "16:9", Map.of(), true, null, null);
        StepVerifier.create(gateway.videoGeneration(channel, CREDENTIALS, generation))
                .assertNext(task -> assertThat(task.taskId()).isEqualTo("task-1"))
                .verifyComplete();
        StepVerifier.create(gateway.videoContext(channel, CREDENTIALS, generation))
                .assertNext(task -> assertThat(task.taskId()).isEqualTo("task-1"))
                .verifyComplete();

        var regeneration = new AiVideoRegenerationRequest(
                "MiniMax-H3", "source-1", List.of(), "2K", true, null, null);
        StepVerifier.create(gateway.videoRegeneration(channel, CREDENTIALS, regeneration))
                .assertNext(task -> assertThat(task.taskId()).isEqualTo("task-1"))
                .verifyComplete();
    }

    @Test
    void deletesVideoTasks() {
        when(http.deleteJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"task_id": "task-1", "action": "deleted", "status": "deleted"}
                        """));

        StepVerifier.create(gateway.deleteVideoTask(channel, CREDENTIALS, "task-1"))
                .assertNext(result -> {
                    assertThat(result.taskId()).isEqualTo("task-1");
                    assertThat(result.action()).isEqualTo("deleted");
                })
                .verifyComplete();

        verify(http).deleteJson(
                eq(URI.create(BASE + "/v2/video_generation/task-1")),
                eq(CREDENTIALS),
                eq(Map.of()),
                any(Duration.class));
    }

    @Test
    void generatesLegacyImages() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "id": "03ff3cd0820949eb8a410056b5f21d38",
                          "data": {"image_urls": ["https://example.com/one.jpg"]},
                          "metadata": {"success_count": "1", "failed_count": "0"},
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        var request = new AiLegacyImageRequest(
                "image-01",
                "a cat",
                List.of(new AiImageSubjectReference("character", "https://example.com/ref.jpg")),
                Map.of(),
                "16:9",
                null,
                null,
                "url",
                42L,
                1,
                true,
                false);
        StepVerifier.create(gateway.legacyImageGeneration(channel, CREDENTIALS, request))
                .assertNext(result -> {
                    assertThat(result.id()).isEqualTo("03ff3cd0820949eb8a410056b5f21d38");
                    assertThat(result.data().imageUrls())
                            .containsExactly("https://example.com/one.jpg");
                    assertThat(result.metadata().successCount()).isEqualTo(1);
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(http).postJson(
                eq(URI.create(BASE + "/v1/image_generation")),
                eq(CREDENTIALS),
                eq(Map.of()),
                body.capture(),
                any(Duration.class));
        @SuppressWarnings("unchecked")
        var values = (Map<String, Object>) body.getValue();
        assertThat(values).containsEntry("model", "image-01").containsEntry("seed", 42L);
        assertThat((List<?>) values.get("subject_reference")).hasSize(1);
    }

    @Test
    void createsAndReadsLegacyVideoTasks() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"task_id": "106916112212032",
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """));
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "task_id": "106916112212032",
                          "status": "Success",
                          "file_id": "176844028768320",
                          "video_width": 1920,
                          "video_height": 1080,
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        var request = new AiLegacyVideoRequest(
                "MiniMax-Hailuo-2.3",
                "a cat dives",
                true,
                false,
                6,
                "1080P",
                null,
                false,
                "https://example.com/first.jpg",
                "https://example.com/last.jpg",
                List.of());
        StepVerifier.create(gateway.legacyVideoGeneration(channel, CREDENTIALS, request))
                .assertNext(task -> assertThat(task.taskId()).isEqualTo("106916112212032"))
                .verifyComplete();
        StepVerifier.create(gateway.legacyVideoTask(channel, CREDENTIALS, "106916112212032"))
                .assertNext(task -> {
                    assertThat(task.status()).isEqualTo("Success");
                    assertThat(task.fileId()).isEqualTo("176844028768320");
                    assertThat(task.videoWidth()).isEqualTo(1920);
                })
                .verifyComplete();
    }

    @Test
    void createsAndReadsVideoAgentTasks() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"task_id": "401047179385389059",
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """));
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "task_id": "401047179385389059",
                          "status": "Success",
                          "video_url": "https://cdn/video.mp4",
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        var request = new AiVideoAgentRequest(
                "393769180141805569",
                List.of(new AiVideoAgentInput("狮子")),
                List.of(new AiVideoAgentInput("https://example.com/image.jpg")),
                null);
        StepVerifier.create(gateway.videoAgentGeneration(channel, CREDENTIALS, request))
                .assertNext(task -> assertThat(task.taskId()).isEqualTo("401047179385389059"))
                .verifyComplete();
        StepVerifier.create(gateway.videoAgentTask(channel, CREDENTIALS, "401047179385389059"))
                .assertNext(task -> {
                    assertThat(task.status()).isEqualTo("Success");
                    assertThat(task.videoUrl()).isEqualTo("https://cdn/video.mp4");
                })
                .verifyComplete();
    }

    @Test
    void managesAsynchronousSpeechTasks() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"task_id": "95157322514444", "file_id": 95157322514496,
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """));
        when(http.getJson(any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {"task_id": 95157322514444, "status": "Success", "file_id": 95157322514496,
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """));

        var request = new AiSpeechAsyncRequest(
                "speech-2.8-hd",
                "hello",
                null,
                Map.of("voice_id", "male-qn-qingse"),
                null,
                null,
                "Chinese",
                null,
                false);
        StepVerifier.create(gateway.speechAsyncCreate(channel, CREDENTIALS, request))
                .assertNext(submitted -> {
                    assertThat(submitted.taskId()).isEqualTo("95157322514444");
                    assertThat(submitted.fileId()).isEqualTo(95157322514496L);
                })
                .verifyComplete();
        StepVerifier.create(gateway.speechAsyncTask(channel, CREDENTIALS, "95157322514444"))
                .assertNext(task -> {
                    assertThat(task.status()).isEqualTo("Success");
                    assertThat(task.fileId()).isEqualTo(95157322514496L);
                })
                .verifyComplete();

        verify(http).getJson(
                eq(URI.create(BASE + "/v1/query/t2a_async_query_v2?task_id=95157322514444")),
                eq(CREDENTIALS),
                eq(Map.of()),
                any(Duration.class));
    }

    @Test
    void rejectsAsyncSpeechWithoutText() {
        var request = new AiSpeechAsyncRequest(
                "speech-2.8-hd", null, null, Map.of("voice_id", "male-qn-qingse"),
                null, null, null, null, null);

        StepVerifier.create(gateway.speechAsyncCreate(channel, CREDENTIALS, request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("必须提供其一"))
                .verify();

        verify(http, never()).postJson(any(), any(), any(), any(), any(Duration.class));
    }

    @Test
    void preprocessesMusicCovers() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "cover_feature_id": "a1b2c3d4",
                          "formatted_lyrics": "[Verse 1] hello",
                          "structure_result": "{\\\"num_segments\\\":1}",
                          "audio_duration": 90,
                          "trace_id": "trace-cover",
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        StepVerifier.create(gateway.musicCover(
                        channel,
                        CREDENTIALS,
                        new AiMusicCoverRequest("music-cover", "https://example.com/song.mp3", null)))
                .assertNext(result -> {
                    assertThat(result.coverFeatureId()).isEqualTo("a1b2c3d4");
                    assertThat(result.formattedLyrics()).contains("hello");
                    assertThat(result.audioDuration()).isEqualTo(90.0);
                })
                .verifyComplete();
    }

    @Test
    void guessesResponseInputTokens() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("{\"input_tokens\":42}"));

        StepVerifier.create(gateway.responsesInputTokens(
                        channel, CREDENTIALS, Map.of("model", "MiniMax-M3", "input", "hello")))
                .expectNext("{\"input_tokens\":42}")
                .verifyComplete();

        verify(http).postJson(
                eq(URI.create(BASE + "/v1/responses/input_tokens")),
                eq(CREDENTIALS),
                eq(Map.of()),
                eq(Map.of("model", "MiniMax-M3", "input", "hello")),
                any(Duration.class));
    }

    @Test
    void generatesMusicAndLyrics() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "data": {"audio": "abcd", "status": 2},
                          "trace_id": "trace-music",
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """))
                .thenReturn(Mono.just("""
                        {
                          "song_title": "Summer",
                          "style_tags": "Pop, Upbeat",
                          "lyrics": "[verse] hello",
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """));

        var music = new AiMusicGenerationRequest(
                "music-3.0", "pop", "[verse] hello", null, null, null, null, null, null,
                null, null, null);
        StepVerifier.create(gateway.musicGeneration(channel, CREDENTIALS, music))
                .assertNext(result -> {
                    assertThat(result.traceId()).isEqualTo("trace-music");
                    assertThat(result.data()).containsEntry("audio", "abcd");
                })
                .verifyComplete();

        StepVerifier.create(gateway.lyricsGeneration(
                        channel, CREDENTIALS, new AiLyricsGenerationRequest("write_full_song", "summer", null, null)))
                .assertNext(result -> {
                    assertThat(result.songTitle()).isEqualTo("Summer");
                    assertThat(result.styleTags()).isEqualTo("Pop, Upbeat");
                })
                .verifyComplete();
    }

    @Test
    void managesVoices() {
        when(http.postJson(any(), any(), any(), any(), any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "system_voice": [
                            {"voice_id": "Chinese (Mandarin)_Reliable_Executive", "voice_name": "沉稳高管",
                             "description": ["可靠"], "created_time": "1970-01-01"}
                          ],
                          "base_resp": {"status_code": 0, "status_msg": "success"}
                        }
                        """))
                .thenReturn(Mono.just("""
                        {"voice_id": "MiniMax001", "created_time": "1728962464",
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """))
                .thenReturn(Mono.just("""
                        {"voice_id": "ttv-voice-1", "trial_audio": "abcd",
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """))
                .thenReturn(Mono.just("""
                        {"demo_audio": "https://example.com/demo.mp3",
                         "base_resp": {"status_code": 0, "status_msg": "success"}}
                        """));

        StepVerifier.create(gateway.voiceList(
                        channel, CREDENTIALS, new AiVoiceListRequest("all")))
                .assertNext(voices -> {
                    assertThat(voices.systemVoices()).hasSize(1);
                    assertThat(voices.systemVoices().getFirst().voiceName()).isEqualTo("沉稳高管");
                })
                .verifyComplete();
        StepVerifier.create(gateway.voiceDelete(
                        channel, CREDENTIALS, new AiVoiceDeleteRequest("voice_cloning", "MiniMax001")))
                .assertNext(deletion -> {
                    assertThat(deletion.voiceId()).isEqualTo("MiniMax001");
                    assertThat(deletion.createdTime()).isEqualTo("1728962464");
                })
                .verifyComplete();
        StepVerifier.create(gateway.voiceDesign(
                        channel, CREDENTIALS, new AiVoiceDesignRequest("低沉男声", "你好", null, null)))
                .assertNext(design -> {
                    assertThat(design.voiceId()).isEqualTo("ttv-voice-1");
                    assertThat(design.trialAudio()).isEqualTo("abcd");
                })
                .verifyComplete();
        StepVerifier.create(gateway.voiceClone(
                        channel,
                        CREDENTIALS,
                        new AiVoiceCloneRequest(
                                123L,
                                "MiniMax001",
                                new AiVoiceClonePrompt(456L, "hello"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)))
                .assertNext(clone -> assertThat(clone.demoAudio())
                        .isEqualTo("https://example.com/demo.mp3"))
                .verifyComplete();
    }

    @Test
    void rejectsInvalidVoiceIdentifierBeforeCallingTheProvider() {
        StepVerifier.create(gateway.voiceClone(
                        channel,
                        CREDENTIALS,
                        new AiVoiceCloneRequest(
                                123L, "1bad", null, null, null, null, null, null, null, null, null)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("音色标识"))
                .verify();

        verify(http, never()).postJson(any(), any(), any(), any(), any(Duration.class));
    }

    private static String text(Flux<DataBuffer> audio) {
        return DataBufferUtils.join(audio)
                .map(buffer -> {
                    var bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .block();
    }

    private static AiChannelEntity channel() {
        var channel = new AiChannelEntity();
        channel.setProvider("minimax");
        channel.setProtocol("messages");
        channel.setBaseUrl(BASE);
        channel.setTimeoutMs(30_000);
        return channel;
    }
}
