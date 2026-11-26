package cn.tolim.pudans.core.ai.provider;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public record AiSpeech(String contentType, Flux<DataBuffer> audio) {}
