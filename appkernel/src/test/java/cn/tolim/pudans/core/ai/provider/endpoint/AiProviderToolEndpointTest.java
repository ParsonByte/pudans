package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekToolsClient;
import cn.tolim.pudans.core.storage.StorageTransfer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderToolEndpointTest {

    @Mock
    DeepSeekProviderAccess access;

    @Mock
    DeepSeekToolsClient toolsClient;

    @Mock
    StorageTransfer storageTransfer;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderToolEndpoint(access, toolsClient, storageTransfer);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void searchesWeb() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(toolsClient.search(eq(provider), eq(account), any()))
                .thenReturn(Mono.just(new DeepSeekToolsClient.SearchResult(List.of(
                        new DeepSeekToolsClient.SearchItem(
                                2.4, "Title", "https://example.com", "snippet", "summary",
                                "", "Site", null, "2026-06-01", 3)))));

        webClient.post()
                .uri("/aiproviders/provider-a/tools/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "deepseek", "count", 5))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.results[0].title").isEqualTo("Title");
    }

    @Test
    void recognizesStoredDocument() {
        var provider = provider();
        var account = account();
        var file = storedFile();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(storageTransfer.getFile("storage-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes(eq("storage-1"), anyInt()))
                .thenReturn(Mono.just("pdf".getBytes(StandardCharsets.UTF_8)));
        when(toolsClient.ocr(
                        eq(provider), eq(account), eq("report.pdf"), eq("application/pdf"),
                        any(), eq("text_recognition")))
                .thenReturn(Mono.just(new DeepSeekToolsClient.OcrResult(
                        "ocr-1", "ocr.result", 1700000000L, "text_recognition", 1,
                        List.of(new DeepSeekToolsClient.OcrPage(0, "Hello", "stop")),
                        new DeepSeekToolsClient.OcrUsage(3, 1, 4))));

        webClient.post()
                .uri("/aiproviders/provider-a/tools/ocr")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("storageFileId", "storage-1", "purpose", "text_recognition"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("ocr-1")
                .jsonPath("$.pages[0].text").isEqualTo("Hello");
    }

    @Test
    void streamsSpeech() {
        var provider = provider();
        var account = account();
        var audio = "audio".getBytes(StandardCharsets.UTF_8);
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(toolsClient.tts(eq(provider), eq(account), eq("hello"), eq("stella"), eq("wav"), eq(true)))
                .thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(audio)));

        webClient.post()
                .uri("/aiproviders/provider-a/tools/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "text", "hello",
                        "voiceId", "stella",
                        "format", "wav",
                        "filterMarkdown", true))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("audio/wav"))
                .expectBody()
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .isEqualTo(audio));
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("DeepSeek");
        provider.getSpec().setProtocol(AiProvider.Protocol.DEEPSEEK);
        provider.getSpec().setBaseUrl("https://api.deepseek.com");
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        return account;
    }

    private static StorageFile storedFile() {
        var file = new StorageFile();
        file.setMetadata(metadata("storage-1"));
        file.getSpec().setPolicy("default");
        file.getSpec().setStorageKey("key");
        file.getSpec().setOriginalName("report.pdf");
        file.getSpec().setContentType("application/pdf");
        file.getSpec().setState(StorageFileState.ACTIVE);
        return file;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
