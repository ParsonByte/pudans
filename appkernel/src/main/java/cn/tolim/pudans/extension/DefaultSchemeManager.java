package cn.tolim.pudans.extension;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.SchemeManager;
import cn.tolim.pudans.extension.contract.index.IndexSpecs;
import cn.tolim.pudans.extension.contract.index.ValueIndexSpec;
import cn.tolim.pudans.extension.event.SchemeAddedEvent;
import cn.tolim.pudans.extension.event.SchemeRemovedEvent;
import cn.tolim.pudans.extension.index.IndexEngine;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class DefaultSchemeManager implements SchemeManager {

    private final List<Scheme> schemes;

    private final IndexEngine indexEngine;

    private final ApplicationEventPublisher eventPublisher;

    // 注入索引引擎与事件发布器并初始化方案列表。
    public DefaultSchemeManager(IndexEngine indexEngine, ApplicationEventPublisher eventPublisher) {
        this.indexEngine = indexEngine;
        this.eventPublisher = eventPublisher;


        schemes = new CopyOnWriteArrayList<>();
    }

    // 注册方案及其索引规格，并广播方案新增事件。
    @Override
    public <E extends Extension> void register(Class<E> type, Consumer<IndexSpecs<E>> specsConsumer) {
        var scheme = Scheme.buildFromType(type);
        if (schemes.contains(scheme)) {
            return;
        }
        var indexSpecs = new DefaultIndexSpecs<E>();
        if (specsConsumer != null) {
            specsConsumer.accept(indexSpecs);
        }
        indexEngine.getIndicesManager().add(type, indexSpecs.getIndexSpecs());
        schemes.add(scheme);
        eventPublisher.publishEvent(new SchemeAddedEvent(this, scheme));
    }

    // 移除方案与索引，并广播方案移除事件。
    @Override
    public void unregister(Scheme scheme) {
        if (schemes.contains(scheme)) {
            indexEngine.getIndicesManager().remove(scheme.type());
            schemes.remove(scheme);
            eventPublisher.publishEvent(new SchemeRemovedEvent(this, scheme));
        }
    }

    // 返回不可修改的方案快照列表。
    @Override
    public List<Scheme> schemes() {
        return Collections.unmodifiableList(schemes);
    }

    private static class DefaultIndexSpecs<E extends Extension> implements IndexSpecs<E> {

        private final Map<String, ValueIndexSpec<E, ?>> specMap;

        private DefaultIndexSpecs() {
            this.specMap = new HashMap<>();
        }

        // 保证同名索引规格只注册一次。
        @Override
        public <K extends Comparable<K>> void add(ValueIndexSpec<E, K> indexSpec) {
            Assert.isTrue(
                    !specMap.containsKey(indexSpec.getName()),
                    "名为 " + indexSpec.getName() + " 的索引规格已存在。");
            this.specMap.put(indexSpec.getName(), indexSpec);
        }

        // 返回已收集的索引规格集合。
        @Override
        public List<ValueIndexSpec<E, ?>> getIndexSpecs() {
            return specMap.values().stream().toList();
        }
    }
}
