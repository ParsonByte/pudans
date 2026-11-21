package cn.tolim.pudans.extension.contract.controller;

import java.time.Duration;

public interface Reconciler<R> {

    // 处理单个请求并返回是否重新入队。
    Result reconcile(R request);

    // 通过构建器完成控制器装配。
    Controller setupWith(ControllerBuilder builder);

    record Request(String name) {}

    record Result(boolean reEnqueue, Duration retryAfter) {

        // 构造不再重试的结果。
        public static Result doNotRetry() {
            return new Result(false, null);
        }

        // 构造带重试延迟的结果。
        public static Result requeue(Duration retryAfter) {
            return new Result(true, retryAfter);
        }
    }
}
