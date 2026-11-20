package cn.tolim.pudans.core.ai.harness.sandbox.snapshot;

public interface SandboxSnapshotSpec {

    SandboxSnapshot forSession(String snapshotId);
}