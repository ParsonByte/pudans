package cn.tolim.pudans.core.extension.index;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class LockCache {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

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

    private void release(String key, Entry entry) {
        entries.computeIfPresent(key, (ignored, current) -> {
            if (current != entry) {
                return current;
            }
            current.refCount--;
            return current.refCount <= 0 ? null : current;
        });
    }

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
    }

    final class Handle implements AutoCloseable {

        private final String key;

        private final Entry entry;

        private final Lock lock;

        private boolean closed;

        private Handle(String key, Entry entry, Lock lock) {
            this.key = key;
            this.entry = entry;
            this.lock = lock;
        }

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
