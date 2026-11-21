package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.exception.SchemeNotFoundException;
import cn.tolim.pudans.extension.contract.index.IndexSpecs;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface SchemeManager {


    // 注册类型并使用默认索引配置。
    default <E extends Extension> void register(Class<E> type) {
        register(type, null);
    }

    // 按 Scheme 的类型注册，索引配置使用默认值。
    default void register(Scheme scheme) {
        register(scheme.type(), null);
    }

    // 注册类型并允许定制索引配置。
    <E extends Extension> void register(Class<E> type, @Nullable Consumer<IndexSpecs<E>> specsConsumer);

    // 移除指定 Scheme 的注册。
    void unregister(Scheme scheme);

    // 返回已注册 Scheme 的数量。
    default int size() {
        return schemes().size();
    }

    // 返回全部已注册 Scheme 的快照。
    List<Scheme> schemes();

    // 按 GVK 查找 Scheme，未命中返回空。
    default Optional<Scheme> fetch(GroupVersionKind gvk) {
        return schemes().stream()
                .filter(scheme -> Objects.equals(scheme.groupVersionKind(), gvk))
                .findFirst();
    }

    // 按 GVK 获取 Scheme，缺失时抛出异常。
    default Scheme get(GroupVersionKind gvk) {
        return fetch(gvk).orElseThrow(() -> new SchemeNotFoundException(gvk));
    }

    // 由类型注解推导 GVK 后获取 Scheme。
    default Scheme get(Class<? extends Extension> type) {
        var gvk = Scheme.getGvkFromType(type);
        return get(new GroupVersionKind(gvk.group(), gvk.version(), gvk.kind()));
    }

    // 由扩展实例的 GVK 获取 Scheme。
    default Scheme get(Extension ext) {
        var gvk = ext.groupVersionKind();
        return get(gvk);
    }
}
