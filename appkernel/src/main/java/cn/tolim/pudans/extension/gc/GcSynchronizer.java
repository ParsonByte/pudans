package cn.tolim.pudans.extension.gc;

import static cn.tolim.pudans.extension.contract.index.query.Queries.isNull;
import static cn.tolim.pudans.extension.contract.index.query.Queries.not;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.SchemeManager;
import cn.tolim.pudans.extension.contract.Watcher;
import cn.tolim.pudans.extension.contract.controller.RequestQueue;
import cn.tolim.pudans.extension.contract.controller.Synchronizer;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.extension.event.SchemeAddedEvent;
import java.util.List;
import org.springframework.context.ApplicationListener;
import org.springframework.data.domain.Sort;

class GcSynchronizer implements Synchronizer<GcRequest>, ApplicationListener<SchemeAddedEvent> {

    private final ExtensionClient client;

    private final SchemeManager schemeManager;
    private final Watcher watcher;
    private boolean disposed = false;
    private boolean started = false;

    // 装配客户端并创建队列监听器。
    GcSynchronizer(ExtensionClient client, RequestQueue<GcRequest> queue, SchemeManager schemeManager) {
        this.client = client;
        this.schemeManager = schemeManager;
        this.watcher = new GcWatcher(queue);
    }

    // 幂等释放监听器资源。
    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }
        this.disposed = true;
        this.watcher.dispose();
    }

    // 返回监听器是否已释放。
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    // 仅在启动后补发新方案中的待删除对象。
    @Override
    public void onApplicationEvent(SchemeAddedEvent event) {
        if (started) {
            var scheme = event.getScheme();
            listDeleted(scheme.type()).forEach(watcher::onDelete);
        }
    }

    // 注册监听并扫描全部方案的待删除对象。
    @Override
    public void start() {
        if (isDisposed() || started) {
            return;
        }
        this.started = true;
        client.watch(watcher);
        schemeManager.schemes().stream()
                .map(Scheme::type)
                .forEach(type -> listDeleted(type).forEach(watcher::onDelete));
    }

    // 查询已标记删除的扩展并按创建时间排序。
    <E extends Extension> List<E> listDeleted(Class<E> type) {
        var options = new ListOptions().setFieldSelector(FieldSelector.of(not(isNull("metadata.deletionTimestamp"))));

        return client.listAll(type, options, Sort.by(Sort.Order.asc("metadata.creationTimestamp")));
    }
}
