package cn.tolim.pudans.extension.api.controller;

import static org.springframework.data.domain.Sort.Direction.ASC;

import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.ExtensionClient;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Watcher;
import cn.tolim.pudans.extension.api.controller.Reconciler.Request;
import cn.tolim.pudans.extension.api.index.query.Queries;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;

@Slf4j
public class RequestSynchronizer implements Synchronizer<Request> {


    private static final Integer DEFAULT_BATCH_SIZE = 100;

    private final ExtensionClient client;

    private final Class<? extends Extension> type;

    private final boolean syncAllOnStart;
    private final Watcher watcher;
    private final ListOptions listOptions;
    private volatile boolean disposed = false;
    @Getter
    private volatile boolean started = false;

    public RequestSynchronizer(
            boolean syncAllOnStart,
            ExtensionClient client,
            Extension extension,
            Watcher watcher,
            ListOptions listOptions) {
        this.syncAllOnStart = syncAllOnStart;
        this.client = client;
        this.type = extension.getClass();
        this.watcher = watcher;
        this.listOptions = listOptions;
    }

    @Override
    public void start() {
        if (isDisposed() || started) {
            return;
        }
        log.info("正在启动请求({})同步器...", type);
        started = true;
        // 先注册监听再执行全量快照，避免两者之间发生的变更被漏掉；队列按名称去重。
        client.watch(this.watcher);

        if (syncAllOnStart) {

            int batchSize = DEFAULT_BATCH_SIZE;
            var sort = Sort.by(ASC, "metadata.name");

            var names = client.listTopNames(type, listOptions, sort, batchSize);
            names.forEach(name -> watcher.onAdd(new Request(name)));
            while (names.size() == batchSize) {
                var lastName = names.getLast();
                var augmentedOptions = ListOptions.builder(listOptions)
                        .andQuery(Queries.greaterThan("metadata.name", lastName))
                        .build();
                names = client.listTopNames(type, augmentedOptions, sort, batchSize);
                names.forEach(name -> watcher.onAdd(new Request(name)));
            }
        }
        log.info("请求({})同步器已启动。", type);
    }

    @Override
    public void dispose() {
        disposed = true;
        watcher.dispose();
    }

    @Override
    public boolean isDisposed() {
        return this.disposed;
    }
}
