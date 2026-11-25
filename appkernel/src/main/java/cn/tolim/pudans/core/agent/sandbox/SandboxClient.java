package cn.tolim.pudans.core.agent.sandbox;

public interface SandboxClient {

    SandboxSession create(String workspaceRoot, String template, String region);

    SandboxSession resume(String providerState);
}