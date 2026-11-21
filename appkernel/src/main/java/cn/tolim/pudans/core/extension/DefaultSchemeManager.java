package cn.tolim.pudans.core.extension;

import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.Scheme;
import cn.tolim.pudans.contract.extension.SchemeManager;
import cn.tolim.pudans.contract.extension.index.IndexSpecs;
import cn.tolim.pudans.contract.extension.index.ValueIndexSpec;
import cn.tolim.pudans.core.extension.event.SchemeAddedEvent;
import cn.tolim.pudans.core.extension.event.SchemeRemovedEvent;
import cn.tolim.pudans.core.extension.index.IndexEngine;
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

    public DefaultSchemeManager(IndexEngine indexEngine, ApplicationEventPublisher eventPublisher) {
        this.indexEngine = indexEngine;
        this.eventPublisher = eventPublisher;


        schemes = new CopyOnWriteArrayList<>();
    }

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

    @Override
    public void unregister(Scheme scheme) {
        if (schemes.contains(scheme)) {
            indexEngine.getIndicesManager().remove(scheme.type());
            schemes.remove(scheme);
            eventPublisher.publishEvent(new SchemeRemovedEvent(this, scheme));
        }
    }

    @Override
    public List<Scheme> schemes() {
        return Collections.unmodifiableList(schemes);
    }

    private static class DefaultIndexSpecs<E extends Extension> implements IndexSpecs<E> {

        private final Map<String, ValueIndexSpec<E, ?>> specMap;

        private DefaultIndexSpecs() {
            this.specMap = new HashMap<>();
        }

        @Override
        public <K extends Comparable<K>> void add(ValueIndexSpec<E, K> indexSpec) {
            Assert.isTrue(
                    !specMap.containsKey(indexSpec.getName()),
                    "名为 " + indexSpec.getName() + " 的索引规格已存在。");
            this.specMap.put(indexSpec.getName(), indexSpec);
        }

        @Override
        public List<ValueIndexSpec<E, ?>> getIndexSpecs() {
            return specMap.values().stream().toList();
        }
    }
}
