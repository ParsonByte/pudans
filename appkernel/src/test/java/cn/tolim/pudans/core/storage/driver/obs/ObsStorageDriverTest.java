package cn.tolim.pudans.core.storage.driver.obs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.StorageMultipartRequest;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import com.obs.services.ObsClient;
import com.obs.services.model.InitiateMultipartUploadRequest;
import com.obs.services.model.InitiateMultipartUploadResult;
import com.obs.services.model.SetBucketCorsRequest;

@ExtendWith(MockitoExtension.class)
class ObsStorageDriverTest {

    @Mock
    ObsClient client;

    @TempDir
    Path workDir;

    private ObsStorageDriver driver;

    @BeforeEach
    void setUp() {
        driver = new ObsStorageDriver(policy(), client, workDir);
    }

    @Test
    void advertisesMultipartAndCors() {
        assertThat(driver.capabilities())
                .contains(StorageCapability.MULTIPART_UPLOAD, StorageCapability.CORS_CONFIGURABLE);
    }

    @Test
    void createsANativeMultipartUpload() {
        var result = new InitiateMultipartUploadResult("bucket", "key", "upload-1");
        when(client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(result);

        var upload = driver.createMultipart(new StorageMultipartRequest("key", 10, "text/plain")).block();

        assertThat(upload).isNotNull();
        assertThat(upload.uploadId()).isEqualTo("upload-1");
    }

    @Test
    void configuresCors() {
        driver.configureCors(List.of("https://example.com")).block();

        verify(client).setBucketCors(any(SetBucketCorsRequest.class));
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("obs");
        policy.getSpec().setType(StoragePolicyType.OBS);
        policy.getSpec().setBucketName("bucket");
        policy.getSpec().setServer("obs.example.com");
        return policy;
    }
}
