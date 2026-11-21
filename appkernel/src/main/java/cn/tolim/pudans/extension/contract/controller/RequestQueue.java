package cn.tolim.pudans.extension.contract.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import reactor.core.Disposable;

public interface RequestQueue<E> extends Disposable {

    // 按队列最小延迟加入请求。
    boolean addImmediately(E request);

    // 加入带就绪时间的请求。
    boolean add(DelayedEntry<E> entry);

    // 阻塞取得到期请求。
    DelayedEntry<E> take() throws InterruptedException;

    // 通知队列请求处理完毕。
    void done(E request);

    // 返回队列中的请求数量。
    long size();

    // 查看最早到期的请求但不取出。
    DelayedEntry<E> peek();

    class DelayedEntry<E> implements Delayed {

        private final E entry;

        private final Instant readyAt;

        private final Supplier<Instant> nowSupplier;

        private final Duration retryAfter;

        // 按相对延迟计算就绪时间。
        DelayedEntry(E entry, Duration retryAfter, Supplier<Instant> nowSupplier) {
            this.entry = entry;
            this.readyAt = nowSupplier.get().plusMillis(retryAfter.toMillis());
            this.nowSupplier = nowSupplier;
            this.retryAfter = retryAfter;
        }

        // 按绝对就绪时间构造延迟项。
        public DelayedEntry(E entry, Instant readyAt, Supplier<Instant> nowSupplier) {
            this.entry = entry;
            this.readyAt = readyAt;
            this.nowSupplier = nowSupplier;
            this.retryAfter = Duration.between(nowSupplier.get(), readyAt);
        }

        // 返回距离就绪时间的剩余时长。
        @Override
        public long getDelay(TimeUnit unit) {
            Duration diff = Duration.between(nowSupplier.get(), readyAt);
            return unit.convert(diff);
        }

        // 返回原始重试延迟。
        public Duration getRetryAfter() {
            return retryAfter;
        }

        // 返回可被取出的绝对时间。
        public Instant getReadyAt() {
            return readyAt;
        }

        // 按剩余延迟比较，供延迟队列排序。
        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }

        // 返回包装的请求。
        public E getEntry() {
            return entry;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DelayedEntry<?> that = (DelayedEntry<?>) o;
            return Objects.equals(entry, that.entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entry);
        }
    }
}
