package cn.tolim.pudans.extension.gc;

import static cn.tolim.pudans.extension.api.index.query.Queries.isNull;
import static cn.tolim.pudans.extension.api.index.query.Queries.not;

import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.ExtensionClient;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Scheme;
import cn.tolim.pudans.extension.api.SchemeManager;
import cn.tolim.pudans.extension.api.Watcher;
import cn.tolim.pudans.extension.api.controller.RequestQueue;
import cn.tolim.pudans.extension.api.controller.Synchronizer;
import cn.tolim.pudans.extension.api.router.selector.FieldSelector;
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

    GcSynchronizer(ExtensionClient client, RequestQueue<GcRequest> queue, SchemeManager schemeManager) {
        this.client = client;
        this.schemeManager = schemeManager;
        this.watcher = new GcWatcher(queue);
    }

    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }
        this.disposed = true;
        this.watcher.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void onApplicationEvent(SchemeAddedEvent event) {
        if (started) {
            var scheme = event.getScheme();
            listDeleted(scheme.type()).forEach(watcher::onDelete);
        }
    }

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

    <E extends Extension> List<E> listDeleted(Class<E> type) {
        var options = new ListOptions().setFieldSelector(FieldSelector.of(not(isNull("metadata.deletionTimestamp"))));

        return client.listAll(type, options, Sort.by(Sort.Order.asc("metadata.creationTimestamp")));
    }
}
