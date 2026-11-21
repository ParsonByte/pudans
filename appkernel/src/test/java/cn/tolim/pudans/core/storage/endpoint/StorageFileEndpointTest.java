package cn.tolim.pudans.core.storage.endpoint;

import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageFileEndpointTest {

    @Mock
    StorageTransfer storageTransfer;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new StorageFileEndpoint(storageTransfer);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void movesAFileToTheRecycleBin() {
        when(storageTransfer.trash("file-1")).thenReturn(Mono.just(file(StorageFileState.TRASHED)));

        client.post()
                .uri("/storagefiles/file-1/trash")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.spec.state")
                .isEqualTo("TRASHED");
    }

    @Test
    void restoresAFile() {
        when(storageTransfer.restore("file-1")).thenReturn(Mono.just(file(StorageFileState.ACTIVE)));

        client.post()
                .uri("/storagefiles/file-1/restore")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.spec.state")
                .isEqualTo("ACTIVE");
    }

    @Test
    void purgesAFile() {
        when(storageTransfer.purge("file-1")).thenReturn(Mono.empty());

        client.delete()
                .uri("/storagefiles/file-1/purge")
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void createsADownloadUrl() {
        when(storageTransfer.downloadUrl("file-1", null))
                .thenReturn(Mono.just(new DownloadUrl(
                        "https://pudans.example.com/apis/api.pudans.tolim.cn/v1alpha1/storagefiles/file-1/content?token=token",
                        Instant.now().plusSeconds(300))));

        client.post()
                .uri("/storagefiles/file-1/download-url")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.url")
                .isEqualTo(
                        "https://pudans.example.com/apis/api.pudans.tolim.cn/v1alpha1/storagefiles/file-1/content?token=token");
    }

    private static StorageFile file(StorageFileState state) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        file.getSpec().setState(state);
        return file;
    }
}
