package cn.tolim.pudans.extension.contract.controller;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.ExtensionMatcher;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.WatcherExtensionMatchers;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.util.Assert;

public class ControllerBuilder {

    private final String name;
    private final Reconciler<Request> reconciler;
    private final ExtensionClient client;
    private Duration minDelay;
    private Duration maxDelay;
    private Supplier<Instant> nowSupplier;
    private Extension extension;
    private ExtensionMatcher onAddMatcher;
    private ExtensionMatcher onDeleteMatcher;
    private ExtensionMatcher onUpdateMatcher;
    private ListOptions syncAllListOptions;
    private boolean syncAllOnStart = true;

    private int workerCount = 1;

    // 校验必需依赖，默认以协调器类名命名。
    public ControllerBuilder(Reconciler<Request> reconciler, ExtensionClient client) {
        Assert.notNull(reconciler, "协调器不能为 null");
        Assert.notNull(client, "扩展客户端不能为 null");
        this.name = reconciler.getClass().getName();
        this.reconciler = reconciler;
        this.client = client;
    }

    // 设置请求重试的最小延迟。
    public ControllerBuilder minDelay(Duration minDelay) {
        this.minDelay = minDelay;
        return this;
    }

    // 设置请求重试的最大延迟。
    public ControllerBuilder maxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
        return this;
    }

    // 替换时钟来源，便于测试时间推进。
    public ControllerBuilder nowSupplier(Supplier<Instant> nowSupplier) {
        this.nowSupplier = nowSupplier;
        return this;
    }

    // 设置控制器监听的扩展实例。
    public ControllerBuilder extension(Extension extension) {
        this.extension = extension;
        return this;
    }

    // 设置新增事件的匹配条件。
    public ControllerBuilder onAddMatcher(ExtensionMatcher onAddMatcher) {
        this.onAddMatcher = onAddMatcher;
        return this;
    }

    // 设置删除事件的匹配条件。
    public ControllerBuilder onDeleteMatcher(ExtensionMatcher onDeleteMatcher) {
        this.onDeleteMatcher = onDeleteMatcher;
        return this;
    }

    // 设置更新事件的匹配条件。
    public ControllerBuilder onUpdateMatcher(ExtensionMatcher extensionMatcher) {
        this.onUpdateMatcher = extensionMatcher;
        return this;
    }

    // 控制启动时是否执行全量同步。
    public ControllerBuilder syncAllOnStart(boolean syncAllAtStart) {
        this.syncAllOnStart = syncAllAtStart;
        return this;
    }

    // 设置全量同步使用的选择条件。
    public ControllerBuilder syncAllListOptions(ListOptions syncAllListOptions) {
        this.syncAllListOptions = syncAllListOptions;
        return this;
    }

    // 设置并发处理请求的工作线程数。
    public ControllerBuilder workerCount(int workerCount) {
        this.workerCount = workerCount;
        return this;
    }

    // 组装队列、监听器与控制器实例。
    public Controller build() {
        if (nowSupplier == null) {
            nowSupplier = Instant::now;
        }
        if (minDelay == null || minDelay.isNegative() || minDelay.isZero()) {
            minDelay = Duration.ofMillis(5);
        }
        if (maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()) {
            maxDelay = Duration.ofSeconds(1000);
        }
        Assert.isTrue(minDelay.compareTo(maxDelay) <= 0, "最小延迟必须小于或等于最大延迟");
        Assert.notNull(extension, "扩展不能为 null");
        Assert.notNull(reconciler, "协调器不能为 null");

        var queue = new DefaultQueue<Request>(nowSupplier, minDelay);
        var extensionMatchers = WatcherExtensionMatchers.builder(client, extension.groupVersionKind())
                .onAddMatcher(onAddMatcher)
                .onUpdateMatcher(onUpdateMatcher)
                .onDeleteMatcher(onDeleteMatcher)
                .build();
        var watcher = new ExtensionWatcher(queue, extensionMatchers);
        var synchronizer =
                new RequestSynchronizer(syncAllOnStart, client, extension, watcher, determineSyncAllListOptions());
        return new DefaultController<>(name, reconciler, queue, synchronizer, minDelay, maxDelay, workerCount);
    }

    // 未指定全量条件时使用空条件。
    ListOptions determineSyncAllListOptions() {
        if (syncAllListOptions != null) {
            return syncAllListOptions;
        }
        return new ListOptions();
    }
}
