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
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxApiClient;
import cn.tolim.pudans.core.ai.provider.supplier.minimax.MinimaxProviderAccess;
import cn.tolim.pudans.core.storage.StorageTransfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
class MinimaxApiEndpointTest {

    @Mock
    MinimaxProviderAccess access;

    @Mock
    MinimaxApiClient client;

    @Mock
    StorageTransfer storageTransfer;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new MinimaxApiEndpoint(access, client, storageTransfer, mapper);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsModels() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(client.get(eq(provider), eq(account), eq("/v1/models"), any()))
                .thenReturn(Mono.just(mapper.createObjectNode()
                        .put("object", "list")
                        .set("data", mapper.createArrayNode().add(
                                mapper.createObjectNode().put("id", "MiniMax-M3.1-Flash")))));

        webClient.get()
                .uri("/aiproviders/provider-a/minimax/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].id").isEqualTo("MiniMax-M3.1-Flash");
    }

    @Test
    void generatesImages() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(client.post(eq(provider), eq(account), eq("/v1/images/generations"), any()))
                .thenReturn(Mono.just(mapper.createObjectNode().put("id", "img-1")));

        webClient.post()
                .uri("/aiproviders/provider-a/minimax/images/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", "MiniMax-H3-Image", "prompt", "cat"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("img-1");
    }

    @Test
    void uploadsStoredFile() {
        var provider = provider();
        var account = account();
        var file = storedFile();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(storageTransfer.getFile("storage-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes(eq("storage-1"), anyInt()))
                .thenReturn(Mono.just("video".getBytes(StandardCharsets.UTF_8)));
        var uploadResponse = mapper.createObjectNode();
        uploadResponse.putObject("file").put("file_id", 1);
        when(client.upload(
                        eq(provider),
                        eq(account),
                        eq("/v1/files/upload"),
                        eq(Map.of("purpose", "video_generation")),
                        eq(Map.of()),
                        eq("clip.mp4"),
                        eq("video/mp4"),
                        any()))
                .thenReturn(Mono.just(uploadResponse));

        webClient.post()
                .uri("/aiproviders/provider-a/minimax/files")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("storageFileId", "storage-1", "purpose", "video_generation"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.file.file_id").isEqualTo(1);
    }

    @Test
    void streamsTtsEvents() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(client.postStream(eq(provider), eq(account), eq("/v1/t2a_v2"), any()))
                .thenReturn(Flux.just(mapper.createObjectNode().put("data", "audio")));

        webClient.post()
                .uri("/aiproviders/provider-a/minimax/speech/t2a")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", "hello", "stream", true))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody()
                .consumeWith(result -> assertThat(
                        new String(result.getResponseBody(), StandardCharsets.UTF_8))
                        .contains("data: ")
                        .contains("\"data\":\"audio\""));
    }

    @Test
    void deletesVideoTask() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(client.delete(provider, account, "/v2/video_generation/task-1"))
                .thenReturn(Mono.just(mapper.createObjectNode().put("task_id", "task-1")));

        webClient.delete()
                .uri("/aiproviders/provider-a/minimax/video/v2/task-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.task_id").isEqualTo("task-1");
    }

    @Test
    void downloadsFileContent() {
        var provider = provider();
        var account = account();
        var bytes = "video".getBytes(StandardCharsets.UTF_8);
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(client.content(provider, account, "/v1/files/retrieve_content",
                        Map.of("file_id", "1")))
                .thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));

        webClient.get()
                .uri("/aiproviders/provider-a/minimax/files/content?fileId=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> assertThat(result.getResponseBody()).isEqualTo(bytes));
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("MiniMax");
        provider.getSpec().setProtocol(AiProvider.Protocol.MINIMAX);
        provider.getSpec().setBaseUrl("https://api.minimax.cn");
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
        file.getSpec().setOriginalName("clip.mp4");
        file.getSpec().setContentType("video/mp4");
        file.getSpec().setState(StorageFileState.ACTIVE);
        return file;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
