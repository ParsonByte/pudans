package cn.tolim.pudans.core.ai.provider.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiFetchRequest;
import cn.tolim.pudans.contract.ai.dto.AiFimRequest;
import cn.tolim.pudans.contract.ai.dto.AiSearchRequest;
import cn.tolim.pudans.contract.ai.dto.AiSpeechRequest;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeepSeekGatewayTest {

    private static final Map<String, String> CREDENTIALS = Map.of("apiKey", "secret");

    private static final String SEARCH_RESPONSE = """
            {
              "results": [
                {
                  "rerank_score": 2.4,
                  "title": "DeepSeek V4 model released",
                  "url": "https://example.com/deepseek-v4",
                  "snippet": "DeepSeek V4 is now available.",
                  "summary": "DeepSeek released V4 with long-context support.",
                  "content": "",
                  "site_name": "Example News",
                  "site_icon": "https://example.com/favicon.ico",
                  "date": "2026-06-01T00:00:00+00:00",
                  "authority": 3
                }
              ]
            }
            """;

    private static final String FIM_RESPONSE = """
            {
              "id": "60f16544-b4f2-4149-9326-79ccc6610d67",
              "choices": [
                {
                  "text": "return a + b",
                  "index": 0,
                  "logprobs": null,
                  "finish_reason": "stop"
                }
              ],
              "created": 1793084841,
              "model": "deepseek-flash",
              "system_fingerprint": "e72d3c7f663d3e1383b00b3884cb4501",
              "object": "text_completion",
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 4,
                "total_tokens": 16,
                "prompt_tokens_details": { "cached_tokens": 0 },
                "prompt_cache_hit_tokens": 0,
                "prompt_cache_miss_tokens": 12,
                "safety_tokens": 2,
                "server_tool_use": { "tool_search": 1 }
              }
            }
            """;

    private static final String SEARCH_AND_FETCH_RESPONSE = """
            {
              "title": "DeepSeek Search",
              "content": "# Search\\n\\nOfficial search result."
            }
            """;

    private static final String FILE_RESPONSE = """
            {
              "id": "file-api-0a1b2c3d4e5f60718293a4b5c6d7e8f9",
              "object": "file",
              "bytes": 4,
              "created_at": 1700000000,
              "filename": "note.txt",
              "purpose": "user_data"
            }
            """;

    private static final String ANTHROPIC_FILE_RESPONSE = """
            {
              "id": "file-api-0a1b2c3d4e5f60718293a4b5c6d7e8f9",
              "type": "file",
              "size_bytes": 4,
              "created_at": "2026-01-01T00:00:00+00:00",
              "filename": "note.txt",
              "mime_type": "text/plain"
            }
            """;

    private static final String OCR_RESPONSE = """
            {
              "id": "ocr-9b2f4c1e-5d7a-4e3b-8f21-6c0a9d4e7b12",
              "object": "ocr.result",
              "created": 1762790000,
              "purpose": "text_recognition",
              "page_count": 1,
              "pages": [
                { "index": 0, "text": "DeepSeek API", "finish_reason": "stop" }
              ],
              "usage": { "input_tokens": 1536, "output_tokens": 412, "total_tokens": 1948 }
            }
            """;

    private static final String ASR_RESPONSE = """
            {
              "request_id": "0b8c1c7e-3c0e-4d0f-9d3a-5f2a6f7e8b90",
              "text": "What is the weather like in Shanghai today?",
              "duration_ms": 3200
            }
            """;

    private static final String BALANCE_RESPONSE = """
            {
              "is_available": true,
              "balance_infos": [
                {
                  "currency": "CNY",
                  "total_balance": "110.00",
                  "granted_balance": "10.00",
                  "topped_up_balance": "100.00"
                }
              ]
            }
            """;

    @Mock
    AiProviderHttp http;

    @TempDir
    Path tempDir;

    @Test
    void sendsTheMigratedSearchRequestAndMapsTheResponse() {
        var gateway = gateway();
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        any(AiSearchRequest.class),
                        any(Duration.class)))
                .thenReturn(Mono.just(SEARCH_RESPONSE));

        var request = new AiSearchRequest(
                "DeepSeek Search API", 5, "如何调用搜索接口", "1.2.3.4", true);
        StepVerifier.create(gateway.search(channel(), CREDENTIALS, request))
                .assertNext(response -> {
                    assertThat(response.results()).hasSize(1);
                    var result = response.results().getFirst();
                    assertThat(result.rerankScore()).isEqualTo(2.4);
                    assertThat(result.siteName()).isEqualTo("Example News");
                    assertThat(result.date()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
                    assertThat(result.authority()).isEqualTo(3);
                })
                .verifyComplete();

        var body = ArgumentCaptor.forClass(AiSearchRequest.class);
        var uri = ArgumentCaptor.forClass(URI.class);
        verify(http).postJson(
                uri.capture(), eq(CREDENTIALS), eq(Map.of()), body.capture(), any(Duration.class));
        assertThat(uri.getValue()).isEqualTo(URI.create("https://api.deepseek.com/v1/tools/search"));
        assertThat(body.getValue())
                .isEqualTo(new AiSearchRequest(
                        "DeepSeek Search API", 5, "如何调用搜索接口", "1.2.3.4", true));
    }

    @Test
    void sendsTheMigratedFimRequestAndPreservesExtendedUsage() {
        var gateway = gateway();
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        any(AiFimRequest.class),
                        any(Duration.class)))
                .thenReturn(Mono.just(FIM_RESPONSE));

        StepVerifier.create(gateway.fim(channel(), CREDENTIALS, fim("return a + b", null)))
                .assertNext(result -> {
                    assertThat(result.id()).isEqualTo("60f16544-b4f2-4149-9326-79ccc6610d67");
                    assertThat(result.choices()).hasSize(1);
                    assertThat(result.choices().getFirst().text()).isEqualTo("return a + b");
                    assertThat(result.usage().promptCacheMissTokens()).isEqualTo(12);
                    assertThat(result.usage().safetyTokens()).isEqualTo(2);
                    assertThat(result.usage().serverToolUse()).containsEntry("tool_search", 1);
                })
                .verifyComplete();

        var body = ArgumentCaptor.forClass(AiFimRequest.class);
        var uri = ArgumentCaptor.forClass(URI.class);
        verify(http).postJson(
                uri.capture(),
                eq(CREDENTIALS),
                eq(Map.of()),
                body.capture(),
                any(Duration.class));
        assertThat(uri.getValue())
                .isEqualTo(URI.create("https://api.deepseek.com/beta/completions"));
        assertThat(body.getValue().prompt()).isEqualTo("return a + b");
        assertThat(body.getValue().stream()).isFalse();
    }

    @Test
    void rejectsFimEchoTogetherWithSuffixBeforeCallingTheProvider() {
        StepVerifier.create(gateway().fim(channel(), CREDENTIALS, fim("prompt", "suffix")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-provider-request-invalid");
                })
                .verify();
    }

    @Test
    void callsTheDocumentedFilesAndBalanceApis() {
        var gateway = gateway();
        when(http.postMultipart(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        org.mockito.ArgumentMatchers.<MultiValueMap<String, ?>>any(),
                        any(Duration.class)))
                .thenReturn(Mono.just(FILE_RESPONSE));
        when(http.getJson(any(URI.class), eq(CREDENTIALS), eq(Map.of()), any(Duration.class)))
                .thenReturn(Mono.just(BALANCE_RESPONSE));

        StepVerifier.create(gateway.uploadFile(
                        channel(),
                        CREDENTIALS,
                        "user_data",
                        "note.txt",
                        "text/plain",
                        file("test"),
                        null))
                .assertNext(file -> {
                    assertThat(file.id()).isEqualTo("file-api-0a1b2c3d4e5f60718293a4b5c6d7e8f9");
                    assertThat(file.bytes()).isEqualTo(4);
                })
                .verifyComplete();
        StepVerifier.create(gateway.balance(channel(), CREDENTIALS))
                .assertNext(balance -> {
                    assertThat(balance.available()).isTrue();
                    assertThat(balance.balances()).hasSize(1);
                    assertThat(balance.balances().getFirst().totalBalance()).isEqualTo("110.00");
                })
                .verifyComplete();

        var parts = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(http).postMultipart(
                eq(URI.create("https://api.deepseek.com/files")),
                eq(CREDENTIALS),
                eq(Map.of()),
                parts.capture(),
                any(Duration.class));
        assertThat(parts.getValue().keySet()).containsExactly("purpose", "file");
        var file = (HttpEntity<?>) parts.getValue().getFirst("file");
        assertThat(file.getBody()).isInstanceOf(Resource.class);
        assertThat(((Resource) file.getBody()).getFilename()).isEqualTo("note.txt");
    }

    @Test
    void callsTheAnthropicFilesApiWithItsRequiredAuthenticationAndBetaHeader() {
        var gateway = gateway();
        when(http.postMultipart(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of("anthropic-beta", "files-api-2025-04-14")),
                        org.mockito.ArgumentMatchers.<MultiValueMap<String, ?>>any(),
                        any(Duration.class),
                        eq(AiProviderAuth.X_API_KEY)))
                .thenReturn(Mono.just(ANTHROPIC_FILE_RESPONSE));

        StepVerifier.create(gateway.uploadAnthropicFile(
                        channel(),
                        CREDENTIALS,
                        null,
                        "note.txt",
                        "text/plain",
                        file("test"),
                        null))
                .assertNext(file -> {
                    assertThat(file.id()).isEqualTo("file-api-0a1b2c3d4e5f60718293a4b5c6d7e8f9");
                    assertThat(file.sizeBytes()).isEqualTo(4);
                })
                .verifyComplete();

        verify(http).postMultipart(
                eq(URI.create("https://api.deepseek.com/anthropic/v1/files")),
                eq(CREDENTIALS),
                eq(Map.of("anthropic-beta", "files-api-2025-04-14")),
                org.mockito.ArgumentMatchers.<MultiValueMap<String, ?>>any(),
                any(Duration.class),
                eq(AiProviderAuth.X_API_KEY));
    }

    @Test
    void callsTheDocumentedFetchOcrSpeechAndTranscriptionApis() {
        var gateway = gateway();
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        any(Object.class),
                        any(Duration.class)))
                .thenReturn(Mono.just(SEARCH_AND_FETCH_RESPONSE));
        when(http.postMultipart(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        org.mockito.ArgumentMatchers.<MultiValueMap<String, ?>>any(),
                        any(Duration.class)))
                .thenReturn(Mono.just(OCR_RESPONSE))
                .thenReturn(Mono.just(ASR_RESPONSE));
        when(http.postBinaryJsonStream(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        any(Object.class),
                        any(Duration.class)))
                .thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(
                        "wave".getBytes(StandardCharsets.UTF_8))));

        StepVerifier.create(gateway.fetch(
                        channel(), CREDENTIALS, new AiFetchRequest("https://api-docs.deepseek.com")))
                .assertNext(response -> assertThat(response.title()).isEqualTo("DeepSeek Search"))
                .verifyComplete();
        StepVerifier.create(gateway.ocr(
                        channel(),
                        CREDENTIALS,
                        "text_recognition",
                        "scan.png",
                        "image/png",
                        file("png")))
                .assertNext(result -> {
                    assertThat(result.pageCount()).isEqualTo(1);
                    assertThat(result.pages().getFirst().text()).isEqualTo("DeepSeek API");
                })
                .verifyComplete();
        StepVerifier.create(gateway.synthesize(
                                channel(),
                                CREDENTIALS,
                                new AiSpeechRequest("Hello", "tide", "pcm", false))
                        .flatMapMany(speech -> {
                            assertThat(speech.contentType()).isEqualTo("audio/pcm");
                            return speech.audio().map(buffer -> {
                                var audio = new byte[buffer.readableByteCount()];
                                buffer.read(audio);
                                DataBufferUtils.release(buffer);
                                return new String(audio, StandardCharsets.UTF_8);
                            });
                        }))
                .expectNext("wave")
                .verifyComplete();
        StepVerifier.create(gateway.transcribe(
                        channel(),
                        CREDENTIALS,
                        "speech.wav",
                        "audio/wav",
                        file("wav")))
                .assertNext(result -> assertThat(result.durationMs()).isEqualTo(3200))
                .verifyComplete();

        verify(http).postJson(
                eq(URI.create("https://api.deepseek.com/v1/tools/fetch")),
                eq(CREDENTIALS),
                eq(Map.of()),
                eq(Map.of("url", "https://api-docs.deepseek.com")),
                any(Duration.class));
        verify(http).postBinaryJsonStream(
                eq(URI.create("https://api.deepseek.com/v1/tools/tts")),
                eq(CREDENTIALS),
                eq(Map.of()),
                eq(Map.of("text", "Hello", "voice_id", "tide", "format", "pcm", "filter_markdown", false)),
                any(Duration.class));
    }

    @Test
    void surfacesAnOcrErrorObjectReturnedWithHttpOk() {
        when(http.postMultipart(
                        any(URI.class),
                        eq(CREDENTIALS),
                        eq(Map.of()),
                        org.mockito.ArgumentMatchers.<MultiValueMap<String, ?>>any(),
                        any(Duration.class)))
                .thenReturn(Mono.just("""
                        {
                          "error": {
                            "message": "OCR did not finish in time",
                            "type": "gateway_timeout_error",
                            "param": null,
                            "code": "gateway_timeout_error"
                          }
                        }
                        """));

        StepVerifier.create(gateway().ocr(
                        channel(),
                        CREDENTIALS,
                        "rich_layout_recognition",
                        "scan.pdf",
                        "application/pdf",
                        file("pdf")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-provider-request-failed");
                    assertThat(error.getMessage()).contains("OCR did not finish in time");
                })
                .verify();
    }

    @Test
    void routesTheDocumentedProtocolsAndPreservesServerSentEvents() {
        var gateway = gateway();
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        anyMap(),
                        any(Object.class),
                        any(Duration.class)))
                .thenReturn(Mono.just("{}"));
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        anyMap(),
                        any(Object.class),
                        any(Duration.class),
                        eq(AiProviderAuth.X_API_KEY)))
                .thenReturn(Mono.just("{}"));
        when(http.postSse(
                        any(URI.class),
                        eq(CREDENTIALS),
                        anyMap(),
                        any(Object.class),
                        any(Duration.class)))
                .thenReturn(Flux.just(ServerSentEvent.<String>builder("{\"type\":\"delta\"}")
                        .event("message_delta")
                        .id("event-1")
                        .build()));
        when(http.postSse(
                        any(URI.class),
                        eq(CREDENTIALS),
                        anyMap(),
                        any(Object.class),
                        any(Duration.class),
                        eq(AiProviderAuth.X_API_KEY)))
                .thenReturn(Flux.just(ServerSentEvent.<String>builder("{\"type\":\"delta\"}")
                        .event("message_delta")
                        .id("event-1")
                        .build()));

        StepVerifier.create(gateway.chatCompletions(channel(), CREDENTIALS, Map.of("model", "deepseek-flash")))
                .expectNext("{}")
                .verifyComplete();
        StepVerifier.create(gateway.messages(
                        channel(), CREDENTIALS, Map.of("model", "deepseek-flash"), "files-api-2025-04-14"))
                .expectNext("{}")
                .verifyComplete();
        StepVerifier.create(gateway.responses(channel(), CREDENTIALS, Map.of("model", "deepseek-flash")))
                .expectNext("{}")
                .verifyComplete();
        StepVerifier.create(gateway.chatCompletionsStream(
                        channel(), CREDENTIALS, Map.of("model", "deepseek-flash", "stream", true)))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("message_delta");
                    assertThat(event.id()).isEqualTo("event-1");
                    assertThat(event.data()).isEqualTo("{\"type\":\"delta\"}");
                })
                .verifyComplete();
        StepVerifier.create(gateway.messagesStream(
                        channel(),
                        CREDENTIALS,
                        Map.of("model", "deepseek-flash", "stream", true),
                        "files-api-2025-04-14"))
                .assertNext(event -> assertThat(event.data()).isEqualTo("{\"type\":\"delta\"}"))
                .verifyComplete();
        StepVerifier.create(gateway.responsesStream(
                        channel(), CREDENTIALS, Map.of("model", "deepseek-flash", "stream", true)))
                .assertNext(event -> assertThat(event.data()).isEqualTo("{\"type\":\"delta\"}"))
                .verifyComplete();

        verify(http).postJson(
                eq(URI.create("https://api.deepseek.com/chat/completions")),
                eq(CREDENTIALS),
                eq(Map.of()),
                any(Object.class),
                any(Duration.class));
        verify(http).postJson(
                eq(URI.create("https://api.deepseek.com/anthropic/v1/messages")),
                eq(CREDENTIALS),
                eq(Map.of(
                        "anthropic-version", "2023-06-01",
                        "anthropic-beta", "files-api-2025-04-14")),
                any(Object.class),
                any(Duration.class),
                eq(AiProviderAuth.X_API_KEY));
        verify(http).postJson(
                eq(URI.create("https://api.deepseek.com/responses")),
                eq(CREDENTIALS),
                eq(Map.of()),
                any(Object.class),
                any(Duration.class));
        verify(http).postSse(
                eq(URI.create("https://api.deepseek.com/anthropic/v1/messages")),
                eq(CREDENTIALS),
                eq(Map.of(
                        "anthropic-version", "2023-06-01",
                        "anthropic-beta", "files-api-2025-04-14")),
                any(Object.class),
                any(Duration.class),
                eq(AiProviderAuth.X_API_KEY));
    }

    @Test
    void routesTheBetaChatPrefixAndAnthropicCompatibilityAlias() {
        var gateway = gateway();
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        anyMap(),
                        any(Object.class),
                        any(Duration.class)))
                .thenReturn(Mono.just("{}"));
        when(http.postJson(
                        any(URI.class),
                        eq(CREDENTIALS),
                        anyMap(),
                        any(Object.class),
                        any(Duration.class),
                        eq(AiProviderAuth.X_API_KEY)))
                .thenReturn(Mono.just("{}"));

        StepVerifier.create(gateway.chatCompletions(
                        channel("https://api.deepseek.com/beta"),
                        CREDENTIALS,
                        Map.of("model", "deepseek-flash")))
                .expectNext("{}")
                .verifyComplete();
        StepVerifier.create(gateway.messages(
                        channel("https://api.deepseek.com/anthropic"),
                        CREDENTIALS,
                        Map.of("model", "deepseek-flash"),
                        null,
                        false))
                .expectNext("{}")
                .verifyComplete();

        verify(http).postJson(
                eq(URI.create("https://api.deepseek.com/beta/chat/completions")),
                eq(CREDENTIALS),
                eq(Map.of()),
                any(Object.class),
                any(Duration.class));
        verify(http).postJson(
                eq(URI.create("https://api.deepseek.com/anthropic/messages")),
                eq(CREDENTIALS),
                eq(Map.of("anthropic-version", "2023-06-01")),
                any(Object.class),
                any(Duration.class),
                eq(AiProviderAuth.X_API_KEY));
    }

    private DeepSeekGateway gateway() {
        return new DeepSeekGateway(http, new DeepSeekProvider(http));
    }

    private static AiFimRequest fim(String prompt, String suffix) {
        return new AiFimRequest(
                "deepseek-flash",
                prompt,
                true,
                null,
                64,
                null,
                false,
                null,
                suffix,
                0.2,
                0.9);
    }

    private static AiChannelEntity channel() {
        return channel("https://api.deepseek.com");
    }

    private static AiChannelEntity channel(String baseUrl) {
        var channel = new AiChannelEntity();
        channel.setProvider("deepseek");
        channel.setProtocol("chat-completions");
        channel.setBaseUrl(baseUrl);
        channel.setTimeoutMs(30_000);
        return channel;
    }

    private Path file(String content) {
        try {
            return Files.writeString(tempDir.resolve("test-" + content.hashCode()), content);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
