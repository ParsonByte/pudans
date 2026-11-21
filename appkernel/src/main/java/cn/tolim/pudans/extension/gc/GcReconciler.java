package cn.tolim.pudans.extension.gc;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.SchemeManager;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.DefaultController;
import cn.tolim.pudans.extension.contract.controller.DefaultQueue;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.extension.contract.controller.RequestQueue;
import cn.tolim.pudans.extension.ExtensionConverter;
import cn.tolim.pudans.extension.event.SchemeAddedEvent;
import cn.tolim.pudans.extension.index.IndexEngine;
import cn.tolim.pudans.infra.r2dbc.store.ReactiveExtensionStoreClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
class GcReconciler implements Reconciler<GcRequest> {

    private final ExtensionClient client;

    private final ReactiveExtensionStoreClient storeClient;

    private final ExtensionConverter converter;

    private final IndexEngine indexEngine;

    private final SchemeManager schemeManager;

    private final RequestQueue<GcRequest> queue;

    private final GcSynchronizer synchronizer;

    private final ReactiveTransactionManager transactionManager;

    private Scheduler scheduler;

    // 装配依赖并创建回收队列与同步器。
    GcReconciler(
            ExtensionClient client,
            ReactiveExtensionStoreClient storeClient,
            ExtensionConverter converter,
            SchemeManager schemeManager,
            IndexEngine indexEngine,
            ReactiveTransactionManager transactionManager) {
        this.client = client;
        this.storeClient = storeClient;
        this.converter = converter;
        this.indexEngine = indexEngine;
        this.transactionManager = transactionManager;
        this.queue = new DefaultQueue<>(Instant::now, Duration.ofMillis(500));
        this.synchronizer = new GcSynchronizer(client, queue, schemeManager);
        this.schemeManager = schemeManager;
        this.scheduler = Schedulers.boundedElastic();
    }

    // 读取请求对应扩展，在可删除时执行清理。
    @Override
    public Result reconcile(GcRequest request) {
        log.debug("扩展 {} 正在被删除", request);
        var scheme = schemeManager.get(request.gvk());
        client.fetch(scheme.type(), request.name())
                .filter(deletable())
                .ifPresent(extension -> doDelete(extension).blockOptional(Duration.ofSeconds(30)));
        return null;
    }

    // 在同一事务内删除存储记录与索引。
    private <E extends Extension> Mono<Void> doDelete(E extension) {
        var extensionStore = converter.convertTo(extension);
        var tx = TransactionalOperator.create(transactionManager);

        return storeClient
                .delete(extensionStore.getName(), extensionStore.getVersion())
                .flatMap(deleted -> Mono.fromRunnable(() -> indexEngine.delete(List.of(extension)))
                        .subscribeOn(this.scheduler))
                .as(tx::transactional)
                .then()
                .doOnSuccess(ignored -> log.info(
                        "扩展 {}/{} 已删除",
                        extension.groupVersionKind(),
                        extension.getMetadata().getName()));
    }

    // 使用队列与同步器构建垃圾回收控制器。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return new DefaultController<>(
                "garbage-collector-controller",
                this,
                queue,
                synchronizer,
                Duration.ofMillis(500),
                Duration.ofSeconds(1000),

                10);
    }

    // 方案注册后补扫其中已标记删除的对象。
    @EventListener
    void onSchemeAddedEvent(SchemeAddedEvent event) {
        synchronizer.onApplicationEvent(event);
    }

    // 无终结器且已标记删除的对象才可清理。
    private Predicate<Extension> deletable() {
        return extension -> CollectionUtils.isEmpty(extension.getMetadata().getFinalizers())
                && extension.getMetadata().getDeletionTimestamp() != null;
    }
}
