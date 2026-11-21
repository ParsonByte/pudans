package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.supplier.qianwen.QianwenApiClient.ProxyResponse;
import cn.tolim.pudans.core.storage.StorageTransfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

class QianwenApiEndpointTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final QianwenProviderAccess access = mock(QianwenProviderAccess.class);

    private final QianwenApiClient client = mock(QianwenApiClient.class);

    private final StorageTransfer storageTransfer = mock(StorageTransfer.class);

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var provider = provider();
        var account = new AiProviderAccount();
        when(access.requireProvider("qianwen")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        var endpoint = new QianwenApiEndpoint(access, client, storageTransfer, mapper);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void proxiesJsonRequestsAndForwardsQueryParameters() {
        var queryCaptor = ArgumentCaptor.forClass(MultiValueMap.class);
        when(client.exchange(
                        any(),
                        any(),
                        eq(HttpMethod.POST),
                        eq("/api/v1/services/aigc/text-generation/generation"),
                        queryCaptor.capture(),
                        eq("{\"model\":\"qwen\"}")))
                .thenReturn(Mono.just(new ProxyResponse(
                        201,
                        MediaType.APPLICATION_JSON,
                        "{\"ok\":true}".getBytes(StandardCharsets.UTF_8))));

        webClient
                .post()
                .uri("/aiproviders/qianwen/qianwen/services/aigc/text-generation/generation?foo=bar")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"qwen\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"ok\":true}");

        assertThat(queryCaptor.getValue().getFirst("foo")).isEqualTo("bar");
    }

    @Test
    void proxiesFileContentAsBinary() {
        when(client.exchange(
                        any(),
                        any(),
                        eq(HttpMethod.GET),
                        eq("/compatible-mode/v1/files/file-1/content"),
                        any(),
                        isNull()))
                .thenReturn(Mono.just(new ProxyResponse(
                        200,
                        MediaType.APPLICATION_OCTET_STREAM,
                        new byte[] {1, 2, 3})));

        webClient
                .get()
                .uri("/aiproviders/qianwen/qianwen/files/file-1/content")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .expectBody(byte[].class)
                .isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void uploadsStoredFilesWithARequiredPurpose() {
        var file = storageFile();
        when(storageTransfer.getFile("storage-1")).thenReturn(Mono.just(file));
        when(storageTransfer.readBytes("storage-1", 100 * 1024 * 1024))
                .thenReturn(Mono.just(new byte[] {1, 2}));
        when(client.postMultipart(
                        any(),
                        any(),
                        eq("/compatible-mode/v1/files"),
                        anyMap(),
                        eq("cat.png"),
                        eq("image/png"),
                        eq(new byte[] {1, 2})))
                .thenReturn(Mono.just(new ProxyResponse(
                        200,
                        MediaType.APPLICATION_JSON,
                        "{\"id\":\"file-1\"}".getBytes(StandardCharsets.UTF_8))));

        webClient
                .post()
                .uri("/aiproviders/qianwen/qianwen/files/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"storageFileId\":\"storage-1\",\"purpose\":\"file-extract\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"id\":\"file-1\"}");
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("qianwen");
        provider.setMetadata(metadata);
        provider.getSpec().setProtocol(AiProvider.Protocol.QIANWEN);
        provider.getSpec().setEnabled(true);
        return provider;
    }

    private static StorageFile storageFile() {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("storage-1");
        file.setMetadata(metadata);
        file.getSpec().setOriginalName("cat.png");
        file.getSpec().setContentType("image/png");
        file.getSpec().setState(StorageFileState.ACTIVE);
        return file;
    }
}
