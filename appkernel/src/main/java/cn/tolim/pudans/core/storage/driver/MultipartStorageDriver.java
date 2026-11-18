package cn.tolim.pudans.core.storage.driver;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MultipartStorageDriver {

    Mono<NativeMultipartUpload> createMultipart(StorageMultipartRequest request);

    Mono<UploadPartUrl> presignPart(NativeMultipartUpload upload, int partNumber, Duration ttl);

    Mono<UploadedPart> putPart(NativeMultipartUpload upload, int partNumber, Flux<DataBuffer> body, long size);

    Mono<StorageObject> completeMultipart(NativeMultipartUpload upload, List<UploadedPart> parts);

    Mono<Void> abortMultipart(NativeMultipartUpload upload);

    Mono<List<UploadedPart>> listParts(NativeMultipartUpload upload);

    record StorageMultipartRequest(String key, long size, String contentType) {}

    record NativeMultipartUpload(String uploadId, String key, long chunkSize) {}

    record UploadPartUrl(int partNumber, URI url, Instant expiresAt) {}

    record UploadedPart(int partNumber, String etag, long size) {}
}
