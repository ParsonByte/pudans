package cn.tolim.pudans.extension.gc;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.Watcher;
import cn.tolim.pudans.extension.contract.controller.RequestQueue;

class GcWatcher implements Watcher {

    private final RequestQueue<GcRequest> queue;

    private Runnable disposeHook;

    private boolean disposed = false;

    // 保存请求队列用于投递回收任务。
    GcWatcher(RequestQueue<GcRequest> queue) {
        this.queue = queue;
    }

    // 新增对象带删除标记时立即入队。
    @Override
    public void onAdd(Extension extension) {

        if (!isDisposed() && extension.getMetadata().getDeletionTimestamp() != null) {
            queue.addImmediately(new GcRequest(
                    extension.groupVersionKind(), extension.getMetadata().getName()));
        }
    }

    // 更新后出现删除标记时立即入队。
    @Override
    public void onUpdate(Extension oldExt, Extension newExt) {
        if (!isDisposed() && newExt.getMetadata().getDeletionTimestamp() != null) {
            queue.addImmediately(new GcRequest(
                    newExt.groupVersionKind(), newExt.getMetadata().getName()));
        }
    }

    // 删除事件仍带删除标记时入队清理。
    @Override
    public void onDelete(Extension extension) {
        if (!isDisposed() && extension.getMetadata().getDeletionTimestamp() != null) {
            queue.addImmediately(new GcRequest(
                    extension.groupVersionKind(), extension.getMetadata().getName()));
        }
    }

    // 登记释放回调以便外部联动清理。
    @Override
    public void registerDisposeHook(Runnable dispose) {
        this.disposeHook = dispose;
    }

    // 幂等释放并执行已登记的回调。
    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }
        this.disposed = true;
        if (this.disposeHook != null) {
            this.disposeHook.run();
        }
    }

    // 返回监听器是否已释放。
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
