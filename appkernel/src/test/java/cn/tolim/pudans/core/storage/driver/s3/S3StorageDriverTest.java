package cn.tolim.pudans.core.storage.driver.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.NativeMultipartUpload;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3StorageDriverTest {

    @Mock
    S3AsyncClient client;

    @Mock
    S3Presigner presigner;

    private S3StorageDriver driver;

    @BeforeEach
    void setUp() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("s3");
        policy.getSpec().setType(StoragePolicyType.S3);
        policy.getSpec().setBucketName("bucket");
        driver = new S3StorageDriver(policy, client, presigner);
    }

    @Test
    void advertisesMultipartAndRangeCapabilities() {
        assertThat(driver.capabilities())
                .contains(
                        StorageCapability.MULTIPART_UPLOAD,
                        StorageCapability.PRESIGNED_PARTS,
                        StorageCapability.PRESIGNED_SOURCE,
                        StorageCapability.RANGE_READ,
                        StorageCapability.CORS_CONFIGURABLE);
    }

    @Test
    void readsObjectMetadata() {
        var lastModified = Instant.parse("2026-01-02T03:04:05Z");
        var response = HeadObjectResponse.builder()
                .contentLength(12L)
                .eTag("\"etag\"")
                .lastModified(lastModified)
                .build();
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        var object = driver.stat("uploads/a.txt").block();

        assertThat(object).isNotNull();
        assertThat(object.size()).isEqualTo(12);
        assertThat(object.etag()).isEqualTo("etag");
        assertThat(object.lastModified()).isEqualTo(lastModified);
    }

    @Test
    void listsAllPartPages() {
        var first = ListPartsResponse.builder()
                .isTruncated(true)
                .nextPartNumberMarker(1000)
                .parts(Part.builder().partNumber(1).eTag("e1").size(5L).build())
                .build();
        var second = ListPartsResponse.builder()
                .isTruncated(false)
                .parts(Part.builder().partNumber(1001).eTag("e1001").size(5L).build())
                .build();
        when(client.listParts(any(ListPartsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(first), CompletableFuture.completedFuture(second));

        var parts = driver.listParts(new NativeMultipartUpload("upload-1", "uploads/a.txt", 5)).block();

        assertThat(parts).extracting(UploadedPart::partNumber).containsExactly(1, 1001);
        var captor = ArgumentCaptor.forClass(ListPartsRequest.class);
        verify(client, times(2)).listParts(captor.capture());
        assertThat(captor.getAllValues().get(1).partNumberMarker()).isEqualTo(1000);
    }

    @Test
    void createsAPresignedSourceUrl() throws Exception {
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.com/uploads/a.txt"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        var source = driver.source(
                        "uploads/a.txt", new StorageSourceOptions(true, "a.txt", Duration.ofMinutes(5)))
                .block();

        assertThat(source).isNotNull();
        assertThat(source.url()).hasToString("https://example.com/uploads/a.txt");
        assertThat(source.proxyRequired()).isFalse();
    }
}
