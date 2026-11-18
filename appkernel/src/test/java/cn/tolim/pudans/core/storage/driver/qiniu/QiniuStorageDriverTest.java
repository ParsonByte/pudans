package cn.tolim.pudans.core.storage.driver.qiniu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.StorageDirectUploadRequest;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;

@ExtendWith(MockitoExtension.class)
class QiniuStorageDriverTest {

    @Mock
    BucketManager bucketManager;

    @TempDir
    Path workDir;

    private QiniuStorageDriver driver;

    @BeforeEach
    void setUp() {
        driver = new QiniuStorageDriver(
                policy(), Auth.create("ak", "sk"), null, bucketManager, workDir, WebClient.builder().build());
    }

    @Test
    void advertisesDirectUploadAndRangeCapabilities() {
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
        assertThat(result.provider()).isEqualTo("qiniu");
        assertThat(result.uploadUrl()).isEqualTo("https://upload.qiniup.com");
        assertThat(result.token()).isNotBlank();
    }

    @Test
    void readsFileInfo() throws Exception {
        var info = new FileInfo();
        info.fsize = 5;
        info.hash = "hash";
        info.putTime = 10_000_000L * 1000L;
        when(bucketManager.stat("bucket", "key")).thenReturn(info);

        var object = driver.stat("key").block();

        assertThat(object).isNotNull();
        assertThat(object.size()).isEqualTo(5);
        assertThat(object.etag()).isEqualTo("hash");
        assertThat(object.lastModified()).isEqualTo(java.time.Instant.ofEpochSecond(1000));
    }

    @Test
    void deletesAFile() throws Exception {
        var response = mock(Response.class);
        when(response.isOK()).thenReturn(true);
        when(bucketManager.delete("bucket", "key")).thenReturn(response);

        driver.delete(List.of("key")).block();

        verify(bucketManager).delete(eq("bucket"), eq("key"));
    }

    @Test
    void testsTheBucketConnection() throws Exception {
        when(bucketManager.buckets()).thenReturn(new String[] {"bucket"});

        var result = driver.testConnection().block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("qiniu");
        policy.getSpec().setType(StoragePolicyType.QINIU);
        policy.getSpec().setBucketName("bucket");
        policy.getSpec().setServer("https://cdn.example.com");
        return policy;
    }
}
