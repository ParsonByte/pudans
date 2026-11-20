package cn.tolim.pudans.core.ai.runtime.sandbox;

public record SandboxExecResult(int exitCode, String stdout, String stderr, long durationMs) {

    public SandboxExecResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}