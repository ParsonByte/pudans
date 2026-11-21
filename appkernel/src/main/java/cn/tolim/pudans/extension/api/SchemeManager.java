package cn.tolim.pudans.extension.api;

import cn.tolim.pudans.extension.api.exception.SchemeNotFoundException;
import cn.tolim.pudans.extension.api.index.IndexSpecs;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface SchemeManager {


    default <E extends Extension> void register(Class<E> type) {
        register(type, null);
    }

    default void register(Scheme scheme) {
        register(scheme.type(), null);
    }

    <E extends Extension> void register(Class<E> type, @Nullable Consumer<IndexSpecs<E>> specsConsumer);

    void unregister(Scheme scheme);

    default int size() {
        return schemes().size();
    }

    List<Scheme> schemes();

    default Optional<Scheme> fetch(GroupVersionKind gvk) {
        return schemes().stream()
                .filter(scheme -> Objects.equals(scheme.groupVersionKind(), gvk))
                .findFirst();
    }

    default Scheme get(GroupVersionKind gvk) {
        return fetch(gvk).orElseThrow(() -> new SchemeNotFoundException(gvk));
    }

    default Scheme get(Class<? extends Extension> type) {
        var gvk = Scheme.getGvkFromType(type);
        return get(new GroupVersionKind(gvk.group(), gvk.version(), gvk.kind()));
    }

    default Scheme get(Extension ext) {
        var gvk = ext.groupVersionKind();
        return get(gvk);
    }
}
