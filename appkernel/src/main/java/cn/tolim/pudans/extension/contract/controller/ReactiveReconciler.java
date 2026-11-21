package cn.tolim.pudans.extension.contract.controller;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface ReactiveReconciler<R> extends Reconciler<R> {

    Duration BLOCKING_TIMEOUT = Duration.ofMinutes(2);

    // 返回响应式协调结果。
    Mono<Result> reconcileReactive(R request);

    // 阻塞等待响应式协调完成，超时上限为两分钟。
    @Override
    default Result reconcile(R request) {
        return reconcileReactive(request).block(BLOCKING_TIMEOUT);
    }
}
