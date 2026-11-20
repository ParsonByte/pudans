package cn.tolim.pudans.core.ai.harness.sandbox.snapshot;

import reactor.core.publisher.Mono;

public interface SandboxSnapshot {

    String id();

    Mono<Void> persist(byte[] archive);

    Mono<byte[]> restore();
}