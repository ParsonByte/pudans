package cn.tolim.pudans.extension.index;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class LockCache {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    // 获取主键写锁并增加引用计数。
    Handle acquire(String key) {
        var entry = entries.compute(key, (ignored, existing) -> {
            var result = existing == null ? new Entry() : existing;
            result.refCount++;
            return result;
        });
        var lock = entry.lock.writeLock();
        lock.lock();
        return new Handle(key, entry, lock);
    }

    // 减少引用计数，归零时移除条目。
    private void release(String key, Entry entry) {
        entries.computeIfPresent(key, (ignored, current) -> {
            if (current != entry) {
                return current;
            }
            current.refCount--;
            return current.refCount <= 0 ? null : current;
        });
    }

    // 返回缓存中的锁条目数量。
    int size() {
        return entries.size();
    }

    // 清空全部锁条目。
    void clear() {
        entries.clear();
    }

    final class Handle implements AutoCloseable {

        private final String key;

        private final Entry entry;

        private final Lock lock;

        private boolean closed;

        // 保存锁与键，供关闭时释放。
        private Handle(String key, Entry entry, Lock lock) {
            this.key = key;
            this.entry = entry;
            this.lock = lock;
        }

        // 幂等释放写锁并减少引用计数。
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            lock.unlock();
            release(key, entry);
        }
    }

    private static final class Entry {

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        private int refCount;
    }
}
