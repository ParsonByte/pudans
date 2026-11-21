package cn.tolim.pudans.extension.indexer;

import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.Scheme;
import cn.tolim.pudans.extension.ExtensionConverter;
import cn.tolim.pudans.extension.ExtensionStoreUtil;
import cn.tolim.pudans.extension.event.SchemeAddedEvent;
import cn.tolim.pudans.extension.index.IndexEngine;
import cn.tolim.pudans.extension.index.IndicesInitializer;
import cn.tolim.pudans.infra.r2dbc.store.ExtensionStore;
import cn.tolim.pudans.infra.r2dbc.store.ReactiveExtensionStoreClient;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@Slf4j
class DefaultIndicesInitializer implements IndicesInitializer {

    private final IndexEngine indexEngine;

    private final ReactiveExtensionStoreClient client;

    private final ExtensionConverter extensionConverter;

    DefaultIndicesInitializer(
            IndexEngine indexEngine, ReactiveExtensionStoreClient client, ExtensionConverter extensionConverter) {
        this.indexEngine = indexEngine;
        this.client = client;
        this.extensionConverter = extensionConverter;
    }

    @EventListener
    void onSchemeAddedEvent(SchemeAddedEvent event) {
        var scheme = event.getScheme();
        this.initialize(scheme);
    }

    @Override
    public void initialize(Scheme scheme) {
        doInitialize(scheme);
    }

    public <E extends Extension> void doInitialize(Scheme scheme) {
        var type = (Class<E>) scheme.type();
        var prefix = ExtensionStoreUtil.buildStoreNamePrefix(scheme);
        List<ExtensionStore> extensionStores;
        String nameCursor = null;
        log.info("开始为类型 {} 初始化索引，前缀：{}", type.getName(), prefix);
        var watch = new StopWatch("Initialize indices for " + type.getName());
        var indexedCount = 0L;
        do {
            watch.start("Indexing from " + (nameCursor == null ? "@start" : nameCursor));
            extensionStores = client.listBy(prefix, nameCursor, 100).collectList().block();
            indexEngine.insert(
                    extensionStores.stream().map(es -> this.extensionConverter.convertFrom(type, es))::iterator);
            if (!extensionStores.isEmpty()) {
                nameCursor = extensionStores.getLast().getName();
            }
            indexedCount += extensionStores.size();
            watch.stop();
        } while (!extensionStores.isEmpty());
        log.info(
                "索引总数：{}，初始化摘要：{}",
                indexedCount,
                watch.prettyPrint(TimeUnit.MILLISECONDS));
    }
}
