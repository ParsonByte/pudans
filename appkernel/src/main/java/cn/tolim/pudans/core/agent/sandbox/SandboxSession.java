package cn.tolim.pudans.core.agent.sandbox;

import java.io.InputStream;

public interface SandboxSession {

    String providerSandboxId();

    String workspaceRoot();

    String providerState();

    boolean isRunning();

    void start() throws Exception;

    SandboxGateway.Execution exec(String workdir, String command, int timeoutSeconds)
            throws Exception;

    void stop() throws Exception;

    void shutdown() throws Exception;

    InputStream persistWorkspace() throws Exception;

    // 供应商执行超时需要作为稳定错误类型跨网关传递。
    class ExecTimeoutException extends Exception {

        public ExecTimeoutException(String message) {
            super(message);
        }

        public ExecTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}