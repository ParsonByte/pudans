package cn.tolim.pudans.core.agent.sandbox;

import java.util.concurrent.Callable;

final class SandboxCalls {

    private static final long BASE_BACKOFF_MILLIS = 200;

    private SandboxCalls() {}

    // 对可安全重放的供应商调用执行有限次重试。
    static <T> T withRetries(int maxRetries, Callable<T> call) throws Exception {
        Exception last = null;
        var attempts = Math.max(0, maxRetries) + 1;
        for (var attempt = 0; attempt < attempts; attempt++) {
            try {
                return call.call();
            } catch (Exception error) {
                if (error instanceof SandboxGateway.Failure failure) {
                    throw failure;
                }
                last = error;
                if (attempt + 1 < attempts) {
                    try {
                        Thread.sleep(BASE_BACKOFF_MILLIS * (attempt + 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw error;
                    }
                }
            }
        }
        throw last;
    }
}