package cn.tolim.pudans.core.ai.harness.tool;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class InMemoryAsyncToolRegistry implements AsyncToolRegistry {

    private final ConcurrentMap<String, Task> tasks = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Mono<AsyncToolTask> submit(
            String toolName, Supplier<HarnessToolkit.ToolResult> execution) {
        Objects.requireNonNull(toolName, "工具名称不能为空");
        Objects.requireNonNull(execution, "异步任务不能为空");
        return Mono.fromSupplier(() -> {
            var order = sequence.incrementAndGet();
            var id = "async-tool-" + order;
            var task = new Task(id, toolName, order);
            tasks.put(id, task);
            var submitted = task.snapshot();
            task.start(execution);
            return submitted;
        });
    }

    @Override
    public Mono<AsyncToolTask> read(String taskId) {
        return Mono.justOrEmpty(tasks.get(taskId)).map(Task::snapshot);
    }

    @Override
    public Mono<Boolean> cancel(String taskId) {
        return Mono.fromSupplier(() -> {
            var task = tasks.get(taskId);
            return task != null && task.cancel();
        });
    }

    @Override
    public Mono<List<AsyncToolTask>> list() {
        return Mono.fromSupplier(() -> tasks.values().stream()
                .sorted(Comparator.comparingLong(Task::sequence))
                .map(Task::snapshot)
                .toList());
    }

    private static final class Task {

        private final String id;

        private final String toolName;

        private final long sequence;

        private AsyncToolStatus status = AsyncToolStatus.RUNNING;

        private String output = "";

        private String error = "";

        private Disposable subscription;

        private Task(String id, String toolName, long sequence) {
            this.id = id;
            this.toolName = toolName;
            this.sequence = sequence;
        }

        private void start(Supplier<HarnessToolkit.ToolResult> execution) {
            subscription = Mono.fromSupplier(execution)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(this::complete, this::fail);
        }

        private long sequence() {
            return sequence;
        }

        private synchronized void complete(HarnessToolkit.ToolResult result) {
            if (status != AsyncToolStatus.RUNNING) {
                return;
            }
            if (result.error()) {
                status = AsyncToolStatus.FAILED;
                error = result.output();
            } else {
                status = AsyncToolStatus.COMPLETED;
                output = result.output();
            }
        }

        private synchronized void fail(Throwable failure) {
            if (status != AsyncToolStatus.RUNNING) {
                return;
            }
            status = AsyncToolStatus.FAILED;
            error = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
        }

        private synchronized boolean cancel() {
            if (status != AsyncToolStatus.RUNNING) {
                return false;
            }
            status = AsyncToolStatus.CANCELLED;
            if (subscription != null) {
                subscription.dispose();
            }
            return true;
        }

        private synchronized AsyncToolTask snapshot() {
            return new AsyncToolTask(id, toolName, status, output, error);
        }
    }
}