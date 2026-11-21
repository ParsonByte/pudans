package cn.tolim.pudans.extension.contract.controller;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.Watcher;
import cn.tolim.pudans.extension.contract.WatcherExtensionMatchers;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;

public class ExtensionWatcher implements Watcher {

    private final RequestQueue<Request> queue;
    private final WatcherExtensionMatchers matchers;
    private volatile boolean disposed = false;
    private Runnable disposeHook;

    // 绑定请求队列与事件匹配器。
    public ExtensionWatcher(RequestQueue<Request> queue, WatcherExtensionMatchers matchers) {
        this.queue = queue;
        this.matchers = matchers;
    }

    // 销毁后忽略，否则立即入队。
    @Override
    public void onAdd(Request request) {
        if (isDisposed()) {
            return;
        }
        queue.addImmediately(request);
    }

    // 匹配新增条件后入队协调请求。
    @Override
    public void onAdd(Extension extension) {
        if (isDisposed() || !matchers.onAddMatcher().match(extension)) {
            return;
        }

        queue.addImmediately(new Request(extension.getMetadata().getName()));
    }

    // 匹配更新条件后入队协调请求。
    @Override
    public void onUpdate(Extension oldExtension, Extension newExtension) {
        if (isDisposed() || !matchers.onUpdateMatcher().match(newExtension)) {
            return;
        }

        queue.addImmediately(new Request(newExtension.getMetadata().getName()));
    }

    // 匹配删除条件后入队协调请求。
    @Override
    public void onDelete(Extension extension) {
        if (isDisposed() || !matchers.onDeleteMatcher().match(extension)) {
            return;
        }

        queue.addImmediately(new Request(extension.getMetadata().getName()));
    }

    // 保存销毁回调，在 dispose 时执行。
    @Override
    public void registerDisposeHook(Runnable dispose) {
        this.disposeHook = dispose;
    }

    // 标记销毁并执行回调。
    @Override
    public void dispose() {
        disposed = true;
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
