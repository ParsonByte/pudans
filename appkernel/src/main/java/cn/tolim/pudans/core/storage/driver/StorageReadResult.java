package cn.tolim.pudans.core.storage.driver;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public record StorageReadResult(
        StorageObject object,
        long rangeStart,
        long rangeEnd,
        long contentLength,
        Flux<DataBuffer> body) {}
