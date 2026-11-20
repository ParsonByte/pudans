package cn.tolim.pudans.core.ai.runtime.sandbox;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// 沙箱提供方适配接口；实现不得向调用方暴露供应商凭据。
public interface SandboxProviderAdapter {

    Mono<SandboxHandle> create(SandboxCreateSpec spec);

    Mono<Void> destroy(String sandboxId);

    Mono<SandboxExecResult> exec(String sandboxId, SandboxExecSpec spec);

    Mono<SandboxFile> readFile(String sandboxId, String path);

    Mono<Void> writeFile(String sandboxId, String path, byte[] content);

    Mono<SandboxStatus> status(String sandboxId);

    Flux<SandboxLogEvent> logs(String sandboxId, @Nullable String cursor);
}