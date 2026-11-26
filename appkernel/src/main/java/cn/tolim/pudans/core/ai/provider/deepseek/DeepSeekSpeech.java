package cn.tolim.pudans.core.ai.provider.deepseek;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public record DeepSeekSpeech(String contentType, Flux<DataBuffer> audio) {}
