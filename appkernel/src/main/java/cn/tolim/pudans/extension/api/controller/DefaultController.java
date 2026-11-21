package cn.tolim.pudans.extension.api.controller;

import cn.tolim.pudans.extension.api.controller.RequestQueue.DelayedEntry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.util.Assert;
import org.springframework.util.StopWatch;

@Slf4j
public class DefaultController<R> implements Controller {

    private final String name;

    private final Reconciler<R> reconciler;

    private final Supplier<Instant> nowSupplier;

    private final RequestQueue<R> queue;
    private final Executor executor;
    private final @Nullable Synchronizer<R> synchronizer;
    private final Duration minDelay;
    private final Duration maxDelay;
    private final int workerCount;
    private final AtomicLong workerCounter;
    private volatile boolean disposed = false;
    private volatile boolean started = false;

    public DefaultController(
            String name,
            Reconciler<R> reconciler,
            RequestQueue<R> queue,
            Synchronizer<R> synchronizer,
            Supplier<Instant> nowSupplier,
            Duration minDelay,
            Duration maxDelay,
            ExecutorService executor,
            int workerCount) {
        this(name, reconciler, queue, synchronizer, nowSupplier, minDelay, maxDelay, (Executor) executor, workerCount);
    }

    public DefaultController(
            String name,
            Reconciler<R> reconciler,
            RequestQueue<R> queue,
            Synchronizer<R> synchronizer,
            Supplier<Instant> nowSupplier,
            Duration minDelay,
            Duration maxDelay,
            Executor executor,
            int workerCount) {
        Assert.isTrue(workerCount > 0, "工作线程数不能小于 1");
        this.name = name;
        this.reconciler = reconciler;
        this.nowSupplier = nowSupplier;
        this.queue = queue;
        this.synchronizer = synchronizer;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.executor = executor;
        this.workerCount = workerCount;
        this.workerCounter = new AtomicLong();
    }

    public DefaultController(
            String name,
            Reconciler<R> reconciler,
            RequestQueue<R> queue,
            Synchronizer<R> synchronizer,
            Duration minDelay,
            Duration maxDelay) {
        this(name, reconciler, queue, synchronizer, Instant::now, minDelay, maxDelay, 1);
    }

    public DefaultController(
            String name,
            Reconciler<R> reconciler,
            RequestQueue<R> queue,
            Synchronizer<R> synchronizer,
            Duration minDelay,
            Duration maxDelay,
            int workerCount) {
        this(name, reconciler, queue, synchronizer, Instant::now, minDelay, maxDelay, workerCount);
    }

    public DefaultController(
            String name,
            Reconciler<R> reconciler,
            RequestQueue<R> queue,
            Synchronizer<R> synchronizer,
            Supplier<Instant> nowSupplier,
            Duration minDelay,
            Duration maxDelay,
            int workerCount) {
        this(name, reconciler, queue, synchronizer, nowSupplier, minDelay, maxDelay, executor(name), workerCount);
    }

    private static Executor executor(String name) {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name(name, 1)
                .uncaughtExceptionHandler((t, e) -> log.error("线程中发生未捕获异常：{}", t.getName(), e))
                .factory());
    }

    private static void closeExecutorService(ExecutorService executorService) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {

            executorService.shutdownNow();
            var interrupted = false;
            while (!terminated) {
                try {
                    terminated = executorService.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
                if (!terminated) {
                    executorService.shutdown();
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    @Override
    public void start() {
        if (isStarted() || isDisposed()) {
            log.warn("控制器 {} 已启动或已销毁。", getName());
            return;
        }
        this.started = true;
        if (synchronizer != null) {
            executor.execute(synchronizer::start);
        }
        log.info("正在启动控制器 {}", name);
        IntStream.range(0, getWorkerCount()).mapToObj(i -> new Worker()).forEach(executor::execute);
    }

    @Override
    public void dispose() {
        disposed = true;
        log.info("正在销毁控制器 {}", name);

        if (synchronizer != null) {
            synchronizer.dispose();
        }

        try {


            if (executor instanceof ExecutorService executorService) {
                closeExecutorService(executorService);
            } else if (executor instanceof AutoCloseable closeable) {
                closeable.close();
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("等待控制器 {} 关闭超时", name);
                } else {
                    log.info("控制器 {} 已销毁", name);
                }
            }
        } catch (Exception e) {
            log.warn("等待控制器 {} 关闭时被中断", name);
        } finally {
            queue.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    public boolean isStarted() {
        return started;
    }

    class Worker implements Runnable {

        private final String name;

        Worker() {
            this.name = DefaultController.this.getName() + "-worker-" + workerCounter.incrementAndGet();
        }

        public String getName() {
            return name;
        }

        @Override
        public void run() {
            log.info("控制器工作线程 {} 已启动", this.name);
            while (!isDisposed() && !Thread.currentThread().isInterrupted()) {
                try {
                    var entry = queue.take();
                    Reconciler.Result result;
                    try {
                        log.debug(
                                "{} >>> 正在协调请求 {}，时间 {}", this.name, entry.getEntry(), nowSupplier.get());
                        var watch = new StopWatch(this.name + ":reconcile: " + entry.getEntry());
                        watch.start("reconciliation");
                        result = reconciler.reconcile(entry.getEntry());
                        watch.stop();
                        log.debug(
                                "{} >>> 请求协调完成：{}，结果：{}，耗时：{}",
                                this.name,
                                entry.getEntry(),
                                result,
                                watch.getTotalTimeMillis());
                    } catch (Throwable t) {
                        result = new Reconciler.Result(true, null);
                        if (t instanceof OptimisticLockingFailureException) {
                            log.warn(
                                    "协调请求时发生乐观锁冲突：{}/{}",
                                    this.name,
                                    entry.getEntry());
                        } else if (t instanceof RequeueException re) {
                            log.warn("{}：重新入队 {}，原因：{}", this.name, entry.getEntry(), re.getMessage());
                            result = re.getResult();
                        } else {
                            log.error("协调器 " + this.name + " 因错误中止，正在重新入队...", t);
                        }
                    } finally {
                        queue.done(entry.getEntry());
                    }
                    if (result == null) {
                        result = new Reconciler.Result(false, null);
                    }
                    if (!result.reEnqueue()) {
                        continue;
                    }
                    var retryAfter = result.retryAfter();
                    if (retryAfter == null) {
                        retryAfter = entry.getRetryAfter();
                        if (retryAfter == null
                                || retryAfter.isNegative()
                                || retryAfter.isZero()
                                || retryAfter.compareTo(minDelay) < 0) {

                            retryAfter = minDelay;
                        } else {
                            try {

                                retryAfter = retryAfter.multipliedBy(2);
                            } catch (ArithmeticException e) {
                                retryAfter = maxDelay;
                            }
                        }
                        if (retryAfter.compareTo(maxDelay) > 0) {
                            retryAfter = maxDelay;
                        }
                    }
                    queue.add(new DelayedEntry<>(entry.getEntry(), retryAfter, nowSupplier));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("控制器工作线程 {} 被中断", name);
                }
            }
            log.info("控制器工作线程 {} 已停止", name);
        }
    }
}
