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
import cn.tolim.pudans.contract.storage.resource.StoragePolicy;
import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.NativeMultipartUpload;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.StorageMultipartRequest;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import com.obs.services.ObsClient;
import com.obs.services.model.InitiateMultipartUploadRequest;
import com.obs.services.model.InitiateMultipartUploadResult;
import com.obs.services.model.ListPartsRequest;
import com.obs.services.model.ListPartsResult;
import com.obs.services.model.Multipart;
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
    void listsAllPartPages() {
        var first = new ListPartsResult(
                "bucket",
                "key",
                "upload-1",
                null,
                null,
                null,
                List.of(new Multipart(1, null, "e1", 5L)),
                1000,
                true,
                null,
                "1000");
        var second = new ListPartsResult(
                "bucket",
                "key",
                "upload-1",
                null,
                null,
                null,
                List.of(new Multipart(1001, null, "e1001", 5L)),
                1000,
                false,
                "1000",
                null);
        when(client.listParts(any(ListPartsRequest.class))).thenReturn(first, second);

        var parts = driver
                .listParts(new NativeMultipartUpload("upload-1", "key", 8L * 1024L * 1024L))
                .block();

        assertThat(parts).extracting(UploadedPart::partNumber).containsExactly(1, 1001);
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
