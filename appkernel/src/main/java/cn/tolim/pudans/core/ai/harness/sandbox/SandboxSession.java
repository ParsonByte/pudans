package cn.tolim.pudans.core.ai.harness.sandbox;

import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxHandle;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderAdapter;
import reactor.core.publisher.Mono;

public record SandboxSession(
        SandboxHandle handle, WorkspaceSpec workspace, SandboxProviderAdapter provider) {

    public SandboxSession {
        if (handle == null) {
            throw new IllegalArgumentException("沙箱句柄不能为空。");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("沙箱工作区不能为空。");
        }
        if (provider == null) {
            throw new IllegalArgumentException("沙箱提供方不能为空。");
        }
    }

    public Mono<Void> close() {
        return provider.destroy(handle.sandboxId());
    }
}