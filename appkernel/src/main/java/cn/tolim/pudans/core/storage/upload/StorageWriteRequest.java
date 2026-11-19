package cn.tolim.pudans.core.storage.upload;

import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public record StorageWriteRequest(
        String policy,
        String owner,
        String module,
        String originPath,
        String originalName,
        String contentType,
        long size,
        Map<String, String> attributes,
        Flux<DataBuffer> body,
        boolean overwrite) {}
