package cn.tolim.pudans.core.storage.driver;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MultipartStorageDriver {

    // 在驱动侧初始化多段上传并返回标识。
    Mono<NativeMultipartUpload> createMultipart(StorageMultipartRequest request);

    // 为指定分片生成带有效期的预签名地址。
    Mono<UploadPartUrl> presignPart(NativeMultipartUpload upload, int partNumber, Duration ttl);

    // 通过服务端写入单个分片。
    Mono<UploadedPart> putPart(NativeMultipartUpload upload, int partNumber, Flux<DataBuffer> body, long size);

    // 合并分片并返回完成后的对象信息。
    Mono<StorageObject> completeMultipart(NativeMultipartUpload upload, List<UploadedPart> parts);

    // 终止驱动侧多段上传并释放资源。
    Mono<Void> abortMultipart(NativeMultipartUpload upload);

    // 列出驱动侧已接收的分片。
    Mono<List<UploadedPart>> listParts(NativeMultipartUpload upload);

    // 合并两页分片结果，保持分页顺序。
    static List<UploadedPart> mergeParts(List<UploadedPart> first, List<UploadedPart> second) {
        var merged = new ArrayList<UploadedPart>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    record StorageMultipartRequest(String key, long size, String contentType) {}

    record NativeMultipartUpload(String uploadId, String key, long chunkSize) {}

    record UploadPartUrl(int partNumber, URI url, Instant expiresAt) {}

    record UploadedPart(int partNumber, String etag, long size) {}
}
