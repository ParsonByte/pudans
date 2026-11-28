package cn.tolim.pudans.iam.core.storage;

import cn.tolim.pudans.contract.repository.client.FileRepositoryClient;
import cn.tolim.pudans.contract.repository.dto.FileMetadataResponse;
import cn.tolim.pudans.contract.repository.dto.RepositoryApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryStorageTests {

    private static final String CONTENT_URL =
            "/apis/pudans.tolim.cn/v1alpha1/iam/storage/content?path=file-1";

    private final FileRepositoryClient repositoryClient = mock(FileRepositoryClient.class);

    private final RepositoryStorage storage = new RepositoryStorage(repositoryClient);

    @Test
    void uploadStoresThroughRepositoryAndReturnsIamContentUrl() throws Exception {
        when(repositoryClient.uploadFile(any(MultipartFile.class), isNull(), eq("iam"),
                eq("iam-storage"), isNull(), eq("pudans-iam")))
                .thenReturn(RepositoryApiResult.ok(FileMetadataResponse.builder()
                        .id("file-1").build()));

        String url = storage.upload("avatar.png", new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThat(url).isEqualTo(CONTENT_URL);
        verify(repositoryClient).uploadFile(any(MultipartFile.class), isNull(), eq("iam"),
                eq("iam-storage"), isNull(), eq("pudans-iam"));
    }

    @Test
    void downloadReturnsProxyUrlForFileIdAndKeepsExternalUrls() {
        assertThat(storage.download("file-1")).isEqualTo(CONTENT_URL);
        assertThat(storage.download("https://cdn.example.com/avatar.png"))
                .isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(storage.download("/legacy/path.png")).isEqualTo("/legacy/path.png");
    }

    @Test
    void uploadFailureIsReported() {
        when(repositoryClient.uploadFile(any(MultipartFile.class), isNull(), eq("iam"),
                eq("iam-storage"), isNull(), eq("pudans-iam")))
                .thenReturn(RepositoryApiResult.error("ERROR", "denied"));

        assertThatThrownBy(() -> storage.upload("avatar.png",
                new ByteArrayInputStream(new byte[]{1})))
                .hasMessageContaining("denied");
    }
}