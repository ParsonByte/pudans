package cn.tolim.pudans.core.storage.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.upload.UploadTicket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageTransferEndpointTest {

    @Mock
    StorageTransfer storageTransfer;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new StorageTransferEndpoint(storageTransfer);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void initiatesAnUpload() {
        when(storageTransfer.initiateUpload(any()))
                .thenReturn(Mono.just(new UploadTicket(
                        "file-1",
                        "local",
                        "uploads/key",
                        8L * 1024L * 1024L,
                        1,
                        Instant.now().plusSeconds(600),
                        "token",
                        false,
                        null)));

        client.post()
                .uri("/storagefiles/-/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policy\":\"local\",\"originalName\":\"a.txt\",\"size\":4}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.fileId")
                .isEqualTo("file-1")
                .jsonPath("$.uploadToken")
                .isEqualTo("token");
    }

    @Test
    void issuesAnUploadToken() {
        when(storageTransfer.issueUploadToken("file-1")).thenReturn(Mono.just("new-token"));

        client.post()
                .uri("/storagefiles/file-1/upload/token")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.token")
                .isEqualTo("new-token");
    }

    @Test
    void rejectsAnUnsatisfiableRange() {
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file()));
        when(storageTransfer.sourceUrl(eq("file-1"), any(StorageSourceOptions.class))).thenReturn(Mono.empty());

        client.get()
                .uri("/storagefiles/file-1/content?token=token")
                .header(HttpHeaders.RANGE, "bytes=1000-")
                .exchange()
                .expectStatus()
                .isEqualTo(416);

        verify(storageTransfer, never()).open(eq("file-1"), any());
    }

    @Test
    void proxiesFileContentWithRangeHeaders() {
        var file = file();
        var content = "data".getBytes(StandardCharsets.UTF_8);
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(ByteBuffer.wrap(content));
        var read = new StorageReadResult(
                new StorageObject("uploads/a.txt", 4, Instant.now(), "etag"), 0, 3, 4, Flux.just(buffer));
        when(storageTransfer.getFile("file-1")).thenReturn(Mono.just(file));
        when(storageTransfer.sourceUrl(eq("file-1"), any(StorageSourceOptions.class))).thenReturn(Mono.empty());
        when(storageTransfer.open(eq("file-1"), any(StorageReadOptions.class))).thenReturn(Mono.just(read));

        client.get()
                .uri("/storagefiles/file-1/content?token=token")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueMatches("Content-Disposition", ".*attachment.*")
                .expectBody(String.class)
                .isEqualTo("data");

        verify(storageTransfer).verifyDownload("token", "file-1");
    }

    @Test
    void uploadsAFilePart() {
        when(storageTransfer.putPart(eq("file-1"), eq(1), anyLong(), any()))
                .thenReturn(Mono.just(new UploadedPart(1, "etag", 4)));

        client.put()
                .uri("/storagefiles/file-1/upload/parts/1?token=token")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue("data")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.partNumber")
                .isEqualTo(1);

        verify(storageTransfer).verifyUpload("token", "file-1");
    }

    @Test
    void completesAnUpload() {
        when(storageTransfer.completeUpload(eq("file-1"), any())).thenReturn(Mono.just(file()));

        client.post()
                .uri("/storagefiles/file-1/upload/complete?token=token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"partNumber\":1,\"etag\":\"etag\",\"size\":4}]")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.metadata.name")
                .isEqualTo("file-1");

        verify(storageTransfer).verifyUpload("token", "file-1");
    }

    private static StorageFile file() {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        var spec = file.getSpec();
        spec.setPolicy("local");
        spec.setStorageKey("uploads/a.txt");
        spec.setOriginalName("a.txt");
        spec.setContentType("text/plain");
        spec.setSize(4);
        spec.setState(StorageFileState.ACTIVE);
        return file;
    }
}
