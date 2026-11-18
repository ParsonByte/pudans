package cn.tolim.pudans.core.storage.driver;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public record StoragePutRequest(
        String key, long size, String contentType, Flux<DataBuffer> body, boolean overwrite) {}
