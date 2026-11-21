package cn.tolim.pudans.core.ai.harness.tool;

import cn.tolim.pudans.infra.r2dbc.ai.tool.AiAsyncToolTaskRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 同一请求作用域内的异步工具任务会落库，其他副本可按任务标识读取
@Slf4j
public final class R2dbcAsyncToolRegistry implements AsyncToolRegistry {

    private final AiAsyncToolTaskRepository repository;

    private final String scope;

    private final ConcurrentMap<String, Task> tasks = new ConcurrentHashMap<>();

    public R2dbcAsyncToolRegistry(AiAsyncToolTaskRepository repository, String scope) {
        this.repository = Objects.requireNonNull(repository, "异步工具仓库不能为空");
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("异步工具作用域不能为空。");
        }
        this.scope = scope;
    }

    @Override
    public Mono<AsyncToolTask> submit(
            String toolName, java.util.function.Supplier<HarnessToolkit.ToolResult> execution) {
        Objects.requireNonNull(toolName, "工具名称不能为空");
        Objects.requireNonNull(execution, "异步任务不能为空");
        var id = "async-tool-" + UUID.randomUUID();
        var task = new Task(id, toolName);
        return repository
                .insert(id, scope, toolName)
                .then(Mono.fromCallable(() -> {
                    tasks.put(id, task);
                    task.start(execution);
                    return task.snapshot();
                }));
    }

    @Override
    public Mono<AsyncToolTask> read(String taskId) {
        var local = tasks.get(taskId);
        if (local != null) {
            return Mono.just(local.snapshot());
        }
        return repository.find(scope, taskId);
    }

    @Override
    public Mono<Boolean> cancel(String taskId) {
        var local = tasks.get(taskId);
        if (local != null && local.cancel()) {
            persist(local.snapshot());
            return Mono.just(true);
        }
        return repository.cancel(scope, taskId);
    }

    @Override
    public Mono<List<AsyncToolTask>> list() {
        return repository
                .listByScope(scope)
                .collectList()
                .map(stored -> merge(stored));
    }

    private List<AsyncToolTask> merge(List<AsyncToolTask> stored) {
        var merged = new java.util.LinkedHashMap<String, AsyncToolTask>();
        for (var task : stored) {
            merged.put(task.id(), task);
        }
        for (var local : tasks.values()) {
            merged.put(local.id(), local.snapshot());
        }
        return List.copyOf(merged.values());
    }

    private void persist(AsyncToolTask task) {
        repository.update(task.id(), task.status(), task.output(), task.error()).subscribe(
                null,
                error -> log.debug("异步工具任务 {} 状态落库失败：{}", task.id(), error.getMessage()));
    }

    private final class Task {

        private final String id;

        private final String toolName;

        private AsyncToolStatus status = AsyncToolStatus.RUNNING;

        private String output = "";

        private String error = "";

        private Disposable subscription;

        private Task(String id, String toolName) {
            this.id = id;
            this.toolName = toolName;
        }

        private void start(java.util.function.Supplier<HarnessToolkit.ToolResult> execution) {
            subscription = Mono.fromSupplier(execution)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(this::complete, this::fail);
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
            persist(snapshot());
        }

        private synchronized void fail(Throwable failure) {
            if (status != AsyncToolStatus.RUNNING) {
                return;
            }
            status = AsyncToolStatus.FAILED;
            error = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            persist(snapshot());
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

        private String id() {
            return id;
        }
    }
}