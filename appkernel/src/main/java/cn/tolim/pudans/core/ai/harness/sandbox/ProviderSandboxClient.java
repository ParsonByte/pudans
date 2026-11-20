package cn.tolim.pudans.core.ai.harness.sandbox;

import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxCreateSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxProviderAdapter;
import java.util.LinkedHashMap;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class ProviderSandboxClient implements SandboxClient {

    private final SandboxProviderAdapter provider;

    public ProviderSandboxClient(SandboxProviderAdapter provider) {
        this.provider = Objects.requireNonNull(provider, "沙箱提供方不能为空。");
    }

    @Override
    public Mono<SandboxSession> create(WorkspaceSpec workspace, SandboxCreateSpec runtime) {
        Objects.requireNonNull(workspace, "沙箱工作区不能为空。");
        Objects.requireNonNull(runtime, "沙箱运行配置不能为空。");
        var environment = new LinkedHashMap<String, String>();
        environment.putAll(workspace.environment());
        // 运行时环境变量覆盖工作区默认值
        environment.putAll(runtime.environment());
        var spec = new SandboxCreateSpec(
                runtime.agentName(),
                runtime.image(),
                runtime.entrypoint(),
                runtime.cpu(),
                runtime.memoryMb(),
                runtime.timeoutSeconds(),
                runtime.networkEnabled(),
                workspace.sandboxRoot(),
                environment);
        return provider.create(spec).map(handle -> new SandboxSession(handle, workspace, provider));
    }
}