package cn.tolim.pudans.extension.contract.controller;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface ReactiveReconciler<R> extends Reconciler<R> {

    Duration BLOCKING_TIMEOUT = Duration.ofMinutes(2);

    Mono<Result> reconcileReactive(R request);

    @Override
    default Result reconcile(R request) {
        return reconcileReactive(request).block(BLOCKING_TIMEOUT);
    }
}
