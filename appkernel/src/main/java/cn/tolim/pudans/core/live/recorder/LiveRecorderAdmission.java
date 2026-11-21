package cn.tolim.pudans.core.live.recorder;

import cn.tolim.pudans.core.infra.properties.LiveProperties;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 录制准入：有界线程池加有界等待队列，队满直接拒绝
@Slf4j
@Component
public class LiveRecorderAdmission {

    public enum Admission {
        ADMITTED,
        QUEUED,
        REJECTED
    }

    private final Object lock = new Object();

    private final Set<String> running = new LinkedHashSet<>();

    private final Set<String> queued = new LinkedHashSet<>();

    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    private final int maxConcurrent;

    private final int queueCapacity;

    private final ThreadPoolExecutor executor;

    public LiveRecorderAdmission(LiveProperties properties) {
        var recorder = properties.getRecorder();
        this.maxConcurrent = Math.max(recorder.getMaxConcurrent(), 1);
        this.queueCapacity = Math.max(recorder.getQueueCapacity(), 0);
        this.executor = new ThreadPoolExecutor(
                maxConcurrent,
                maxConcurrent,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(queueCapacity, 1)),
                new RecorderThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public Admission admit(String recordingName, Runnable task) {
        Runnable wrapper = () -> {
            start(recordingName);
            try {
                task.run();
            } finally {
                finish(recordingName);
            }
        };
        synchronized (lock) {
            if (running.contains(recordingName) || queued.contains(recordingName)) {
                return Admission.REJECTED;
            }
            Admission admission;
            if (running.size() < maxConcurrent) {
                running.add(recordingName);
                admission = Admission.ADMITTED;
            } else if (queued.size() < queueCapacity) {
                queued.add(recordingName);
                admission = Admission.QUEUED;
            } else {
                return Admission.REJECTED;
            }
            try {
                futures.put(recordingName, executor.submit(wrapper));
            } catch (RejectedExecutionException error) {
                running.remove(recordingName);
                queued.remove(recordingName);
                log.warn("录制准入被线程池拒绝 [{}]：{}", recordingName, error.getMessage());
                return Admission.REJECTED;
            }
            return admission;
        }
    }

    public boolean cancel(String recordingName) {
        synchronized (lock) {
            if (!queued.remove(recordingName)) {
                return false;
            }
            var future = futures.remove(recordingName);
            return future != null && future.cancel(false);
        }
    }

    public int activeCount() {
        synchronized (lock) {
            return running.size();
        }
    }

    public int queuedCount() {
        synchronized (lock) {
            return queued.size();
        }
    }

    private void start(String recordingName) {
        synchronized (lock) {
            queued.remove(recordingName);
            running.add(recordingName);
        }
    }

    private void finish(String recordingName) {
        synchronized (lock) {
            running.remove(recordingName);
            futures.remove(recordingName);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class RecorderThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, "live-recorder-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
