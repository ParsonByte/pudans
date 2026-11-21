package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.controller.Reconciler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.Disposable;

public interface Watcher extends Disposable {

    // 控制器请求到达事件，默认不处理。
    default void onAdd(Reconciler.Request request) {

    }

    // 新增扩展事件，默认不处理。
    default void onAdd(Extension extension) {

    }

    // 更新扩展事件，默认不处理。
    default void onUpdate(Extension oldExtension, Extension newExtension) {

    }

    // 删除扩展事件，默认不处理。
    default void onDelete(Extension extension) {

    }

    // 注册销毁回调，默认忽略。
    default void registerDisposeHook(Runnable dispose) {}

    class WatcherComposite implements Watcher {

        private final List<Watcher> watchers;

        private volatile boolean disposed = false;

        private Runnable disposeHook;

        // 创建线程安全的监听器集合。
        public WatcherComposite() {
            watchers = new CopyOnWriteArrayList<>();
        }

        // 广播新增事件给所有子监听器。
        @Override
        public void onAdd(Extension extension) {

            watchers.forEach(watcher -> watcher.onAdd(extension));
        }

        // 广播更新事件给所有子监听器。
        @Override
        public void onUpdate(Extension oldExtension, Extension newExtension) {

            watchers.forEach(watcher -> watcher.onUpdate(oldExtension, newExtension));
        }

        // 广播删除事件给所有子监听器。
        @Override
        public void onDelete(Extension extension) {

            watchers.forEach(watcher -> watcher.onDelete(extension));
        }

        // 去重后加入，并绑定自动移除回调。
        public void addWatcher(Watcher watcher) {
            if (!watcher.isDisposed() && !watchers.contains(watcher)) {
                watchers.add(watcher);
                watcher.registerDisposeHook(() -> removeWatcher(watcher));
            }
        }

        // 从广播集合移除监听器。
        public void removeWatcher(Watcher watcher) {
            watchers.remove(watcher);
        }

        // 保存销毁回调，供 dispose 时执行。
        @Override
        public void registerDisposeHook(Runnable dispose) {
            this.disposeHook = dispose;
        }

        // 停止广播并清理所有子监听器。
        @Override
        public void dispose() {
            this.disposed = true;
            this.watchers.clear();
            if (this.disposeHook != null) {
                this.disposeHook.run();
            }
        }

        // 返回是否已销毁。
        @Override
        public boolean isDisposed() {
            return this.disposed;
        }
    }
}
