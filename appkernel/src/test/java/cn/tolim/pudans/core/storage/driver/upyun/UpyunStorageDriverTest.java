package cn.tolim.pudans.core.storage.driver.upyun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.StorageDirectUploadRequest;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.infra.exception.ApiException;
import com.UpYun;

@ExtendWith(MockitoExtension.class)
class UpyunStorageDriverTest {

    @Mock
    UpYun client;

    @TempDir
    Path workDir;

    private UpyunStorageDriver driver;

    @BeforeEach
    void setUp() {
        driver = new UpyunStorageDriver(policy(), client, "operator", "password", workDir);
    }

    @Test
    void advertisesDirectUploadAndRange() {
        assertThat(driver.capabilities())
                .contains(StorageCapability.DIRECT_UPLOAD, StorageCapability.RANGE_READ);
    }

    @Test
    void createsADirectUploadCredential() {
        var result = driver
                .createDirectUpload(new StorageDirectUploadRequest(
                        "key", 5, "text/plain", Instant.now().plusSeconds(600)))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.provider()).isEqualTo("upyun");
        assertThat(result.uploadUrl()).isEqualTo("https://v0.api.upyun.com/bucket");
        assertThat(result.policy()).isNotBlank();
        assertThat(result.signature()).startsWith("UPYUN operator:");
    }

    @Test
    void readsFileInfo() throws Exception {
        when(client.getFileInfo("key")).thenReturn(Map.of("size", "5", "etag", "etag"));

        var object = driver.stat("key").block();

        assertThat(object).isNotNull();
        assertThat(object.size()).isEqualTo(5);
        assertThat(object.etag()).isEqualTo("etag");
    }

    @Test
    void reportsMissingObjectsAsNotFound() throws Exception {
        when(client.getFileInfo("missing")).thenReturn(null);

        assertThatThrownBy(() -> driver.stat("missing").block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-object-not-found"));
    }

    @Test
    void rejectsADownloadLargerThanTheAvailableTemporarySpace() throws Exception {
        when(client.getFileInfo("huge")).thenReturn(Map.of("size", String.valueOf(Long.MAX_VALUE)));

        assertThatThrownBy(() -> driver.get("huge", null).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-temp-space-insufficient"));
        verify(client, never()).readFile(eq("huge"), any());
    }

    @Test
    void deletesAFile() throws Exception {
        when(client.deleteFile(eq("key"), any())).thenReturn(true);

        driver.delete(List.of("key")).block();

        verify(client).deleteFile(eq("key"), any());
    }

    @Test
    void testsTheBucketConnection() throws Exception {
        when(client.getBucketUsage()).thenReturn(1L);

        var result = driver.testConnection().block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("upyun");
        policy.getSpec().setType(StoragePolicyType.UPYUN);
        policy.getSpec().setBucketName("bucket");
        policy.getSpec().setServer("https://v0.api.upyun.com");
        return policy;
    }
}
