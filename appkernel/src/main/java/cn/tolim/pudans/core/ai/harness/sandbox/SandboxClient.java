package cn.tolim.pudans.core.ai.harness.sandbox;

import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxCreateSpec;
import reactor.core.publisher.Mono;

public interface SandboxClient {

    Mono<SandboxSession> create(WorkspaceSpec workspace, SandboxCreateSpec runtime);
}