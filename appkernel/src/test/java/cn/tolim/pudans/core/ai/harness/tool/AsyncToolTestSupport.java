package cn.tolim.pudans.core.ai.harness.tool;

import java.util.concurrent.CountDownLatch;

final class AsyncToolTestSupport {

    private AsyncToolTestSupport() {}

    static AsyncToolTask awaitTerminal(AsyncToolRegistry registry, String id) {
        for (var attempt = 0; attempt < 200; attempt++) {
            var task = registry.read(id).block();
            if (task == null) {
                throw new AssertionError("异步任务不存在：" + id);
            }
            if (task.status() != AsyncToolStatus.RUNNING) {
                return task;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待异步任务时被中断。", error);
            }
        }
        throw new AssertionError("异步任务未在预期时间内结束：" + id);
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待异步任务时被中断。", error);
        }
    }
}