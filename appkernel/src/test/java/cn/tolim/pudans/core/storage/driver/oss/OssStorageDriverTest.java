package cn.tolim.pudans.core.storage.driver.oss;

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
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.NativeMultipartUpload;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.StorageMultipartRequest;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.SetBucketCORSRequest;

@ExtendWith(MockitoExtension.class)
class OssStorageDriverTest {

    @Mock
    OSS client;

    @TempDir
    Path workDir;

    private OssStorageDriver driver;

    @BeforeEach
    void setUp() {
        driver = new OssStorageDriver(policy(), client, workDir);
    }

    @Test
    void advertisesMultipartAndCors() {
        assertThat(driver.capabilities())
                .contains(StorageCapability.MULTIPART_UPLOAD, StorageCapability.CORS_CONFIGURABLE);
    }

    @Test
    void createsANativeMultipartUpload() {
        var result = new InitiateMultipartUploadResult();
        result.setUploadId("upload-1");
        when(client.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(result);

        var upload = driver.createMultipart(new StorageMultipartRequest("key", 10, "text/plain")).block();

        assertThat(upload).isNotNull();
        assertThat(upload.uploadId()).isEqualTo("upload-1");
        assertThat(upload.key()).isEqualTo("key");
    }

    @Test
    void listsNativeParts() {
        var part = new PartSummary();
        part.setPartNumber(1);
        part.setETag("etag");
        part.setSize(5);
        var listing = new PartListing();
        listing.setParts(List.of(part));
        when(client.listParts(any(ListPartsRequest.class))).thenReturn(listing);

        var parts = driver
                .listParts(new NativeMultipartUpload("upload-1", "key", 8L * 1024L * 1024L))
                .block();

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst().partNumber()).isEqualTo(1);
        assertThat(parts.getFirst().etag()).isEqualTo("etag");
    }

    @Test
    void listsAllPartPages() {
        var firstPart = new PartSummary();
        firstPart.setPartNumber(1);
        firstPart.setETag("e1");
        firstPart.setSize(5);
        var first = new PartListing();
        first.setParts(List.of(firstPart));
        first.setTruncated(true);
        first.setNextPartNumberMarker(1000);
        var secondPart = new PartSummary();
        secondPart.setPartNumber(1001);
        secondPart.setETag("e1001");
        secondPart.setSize(5);
        var second = new PartListing();
        second.setParts(List.of(secondPart));
        when(client.listParts(any(ListPartsRequest.class))).thenReturn(first, second);

        var parts = driver
                .listParts(new NativeMultipartUpload("upload-1", "key", 8L * 1024L * 1024L))
                .block();

        assertThat(parts).extracting(UploadedPart::partNumber).containsExactly(1, 1001);
    }

    @Test
    void configuresCors() {
        driver.configureCors(List.of("https://example.com")).block();

        verify(client).setBucketCORS(any(SetBucketCORSRequest.class));
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("oss");
        policy.getSpec().setType(StoragePolicyType.OSS);
        policy.getSpec().setBucketName("bucket");
        policy.getSpec().setServer("https://oss.example.com");
        return policy;
    }
}
