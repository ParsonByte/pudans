package cn.tolim.pudans.contract.extension;

import cn.tolim.pudans.contract.extension.controller.Reconciler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.Disposable;

public interface Watcher extends Disposable {

    default void onAdd(Reconciler.Request request) {

    }

    default void onAdd(Extension extension) {

    }

    default void onUpdate(Extension oldExtension, Extension newExtension) {

    }

    default void onDelete(Extension extension) {

    }

    default void registerDisposeHook(Runnable dispose) {}

    class WatcherComposite implements Watcher {

        private final List<Watcher> watchers;

        private volatile boolean disposed = false;

        private Runnable disposeHook;

        public WatcherComposite() {
            watchers = new CopyOnWriteArrayList<>();
        }

        @Override
        public void onAdd(Extension extension) {

            watchers.forEach(watcher -> watcher.onAdd(extension));
        }

        @Override
        public void onUpdate(Extension oldExtension, Extension newExtension) {

            watchers.forEach(watcher -> watcher.onUpdate(oldExtension, newExtension));
        }

        @Override
        public void onDelete(Extension extension) {

            watchers.forEach(watcher -> watcher.onDelete(extension));
        }

        public void addWatcher(Watcher watcher) {
            if (!watcher.isDisposed() && !watchers.contains(watcher)) {
                watchers.add(watcher);
                watcher.registerDisposeHook(() -> removeWatcher(watcher));
            }
        }

        public void removeWatcher(Watcher watcher) {
            watchers.remove(watcher);
        }

        @Override
        public void registerDisposeHook(Runnable dispose) {
            this.disposeHook = dispose;
        }

        @Override
        public void dispose() {
            this.disposed = true;
            this.watchers.clear();
            if (this.disposeHook != null) {
                this.disposeHook.run();
            }
        }

        @Override
        public boolean isDisposed() {
            return this.disposed;
        }
    }
}
