package cn.tolim.pudans.core.ai.harness.subagent;

import cn.tolim.pudans.core.ai.harness.agent.Agent;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.agent.AgentRunStatus;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.harness.event.AgentEventType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class SubagentRuntime {

    public static final String PARENT_RUN_ID_KEY = "harness.parentRunId";

    private static final Logger log = LoggerFactory.getLogger(SubagentRuntime.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    private final ConcurrentMap<SubagentScope, Registry> registries = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, MutableTask> tasks = new ConcurrentHashMap<>();

    private final SubagentEventListener eventListener;

    public SubagentRuntime() {
        this(null);
    }

    public SubagentRuntime(SubagentEventListener eventListener) {
        this.eventListener = eventListener == null ? (agentKey, event) -> {} : eventListener;
    }

    public SubagentRuntime register(String agentId, SubagentFactory factory) {
        return register(agentId, false, factory);
    }

    public SubagentRuntime register(String agentId, boolean persistSession, SubagentFactory factory) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("子代理标识不能为空。");
        }
        registrations.put(agentId, new Registration(
                persistSession, Objects.requireNonNull(factory, "子代理工厂不能为空")));
        return this;
    }

    public Mono<SubagentTask> spawn(SubagentSpawnRequest request, RuntimeContext parentContext) {
        return Mono.defer(() -> {
            Objects.requireNonNull(request, "子代理启动请求不能为空");
            Objects.requireNonNull(parentContext, "父运行时上下文不能为空");
            var registration = registrations.get(request.agentId());
            if (registration == null) {
                return Mono.error(new IllegalArgumentException("未知子代理：" + request.agentId()));
            }
            if (!request.reusable() && request.task().isBlank()) {
                return Mono.error(new IllegalArgumentException("一次性子代理必须提供任务。"));
            }
            if (!request.reusable() && request.label() != null) {
                return Mono.error(new IllegalArgumentException("一次性子代理不能设置标签。"));
            }
            if (!request.reusable() && registration.persistSession()) {
                return Mono.error(new IllegalArgumentException("一次性子代理不能持久化子会话。"));
            }
            var scope = SubagentScope.from(parentContext);
            var registry = registries.computeIfAbsent(scope, ignored -> new Registry());
            if (registration.persistSession()) {
                var hash = deterministicHash(
                        parentContext.userId(),
                        parentContext.sessionId(),
                        request.parentRunId(),
                        request.agentId(),
                        request.label());
                var key = "agent:" + request.agentId() + ":" + hash;
                var sessionId = "sub-" + hash;
                var existing = existing(registry, key);
                if (existing != null) {
                    return reuse(existing, request, parentContext, scope);
                }
                var managed = create(
                        registry,
                        key,
                        request.agentId(),
                        sessionId,
                        request.label(),
                        request.reusable(),
                        registration,
                        parentContext);
                return executeOrAccept(
                        managed,
                        request.task(),
                        request.mode(),
                        request.timeout(),
                        parentContext,
                        scope);
            }
            var key = "agent:" + request.agentId() + ":" + UUID.randomUUID();
            var sessionId = "sub-" + UUID.randomUUID();
            var managed = create(
                    registry,
                    key,
                    request.agentId(),
                    sessionId,
                    request.label(),
                    request.reusable(),
                    registration,
                    parentContext);
            return executeOrAccept(
                    managed,
                    request.task(),
                    request.mode(),
                    request.timeout(),
                    parentContext,
                    scope);
        });
    }

    public Mono<SubagentTask> send(SubagentSendRequest request, RuntimeContext parentContext) {
        return Mono.defer(() -> {
            Objects.requireNonNull(request, "子代理消息请求不能为空");
            Objects.requireNonNull(parentContext, "父运行时上下文不能为空");
            if (request.message().isBlank()) {
                return Mono.error(new IllegalArgumentException("子代理消息不能为空。"));
            }
            var hasKey = request.agentKey() != null;
            var hasLabel = request.label() != null;
            if (hasKey == hasLabel) {
                return Mono.error(new IllegalArgumentException("agent_key 与 label 必须且只能提供一个。"));
            }
            var scope = SubagentScope.from(parentContext);
            var registry = registries.get(scope);
            if (registry == null) {
                return Mono.error(new IllegalArgumentException("子代理不存在。"));
            }
            var key = hasKey ? request.agentKey() : labelKey(registry, request.label());
            if (key == null) {
                return Mono.error(new IllegalArgumentException("子代理标签不存在：" + request.label()));
            }
            var managed = existing(registry, key);
            if (managed == null) {
                return Mono.error(new IllegalArgumentException("子代理键不存在：" + key));
            }
            if (managed.isReleasing()) {
                return Mono.error(new IllegalStateException("子代理正在释放，无法发送消息。"));
            }
            if (!managed.spawned.reusable()) {
                return Mono.error(new IllegalArgumentException("子代理不可复用：" + key));
            }
            return executeOrAccept(
                    managed, request.message(), request.mode(), request.timeout(), parentContext, scope);
        });
    }

    public Flux<SubagentHandle> list(RuntimeContext parentContext) {
        Objects.requireNonNull(parentContext, "父运行时上下文不能为空");
        return Flux.defer(() -> {
            var registry = registries.get(SubagentScope.from(parentContext));
            if (registry == null) {
                return Flux.empty();
            }
            return Flux.fromStream(existingHandles(registry).stream());
        });
    }

    public Mono<Void> release(String agentKey, String label, RuntimeContext parentContext) {
        return Mono.defer(() -> {
            Objects.requireNonNull(parentContext, "父运行时上下文不能为空");
            var hasKey = agentKey != null && !agentKey.isBlank();
            var hasLabel = label != null && !label.isBlank();
            if (hasKey == hasLabel) {
                return Mono.error(new IllegalArgumentException("agent_key 与 label 必须且只能提供一个。"));
            }
            var scope = SubagentScope.from(parentContext);
            var registry = registries.get(scope);
            if (registry == null) {
                return Mono.error(new IllegalArgumentException("子代理不存在。"));
            }
            ManagedSubagent managed;
            String key;
            synchronized (registry) {
                key = hasKey ? agentKey.trim() : registry.labelToKey.get(lowerLabel(label));
                managed = key == null ? null : registry.agents.get(key);
                if (managed == null || !managed.beginRelease()) {
                    if (managed != null) {
                        return Mono.error(new IllegalStateException("子代理正在运行，无法释放：" + key));
                    }
                    return Mono.error(new IllegalArgumentException(
                            "子代理不存在：" + resolvedReference(agentKey, label)));
                }
                registry.agents.remove(key, managed);
                if (managed.spawned.label() != null) {
                    registry.labelToKey.remove(lowerLabel(managed.spawned.label()), key);
                }
            }
            closeAgent(managed.spawned.agent());
            removeRegistryIfEmpty(scope, registry);
            return Mono.empty();
        });
    }

    public Mono<SubagentTask> task(String taskId, RuntimeContext parentContext) {
        return Mono.defer(() -> {
            var task = scopedTask(taskId, parentContext);
            return Mono.just(task.snapshot());
        });
    }

    public Mono<SubagentTask> awaitTask(String taskId, RuntimeContext parentContext) {
        return Mono.defer(() -> {
            var task = scopedTask(taskId, parentContext);
            return task.await();
        });
    }

    private MutableTask scopedTask(String taskId, RuntimeContext parentContext) {
        Objects.requireNonNull(parentContext, "父运行时上下文不能为空");
        var task = taskId == null ? null : tasks.get(taskId);
        if (task == null || !task.scope.equals(SubagentScope.from(parentContext))) {
            throw new IllegalArgumentException("子代理任务不存在：" + taskId);
        }
        return task;
    }

    private Mono<SubagentTask> reuse(
            ManagedSubagent managed,
            SubagentSpawnRequest request,
            RuntimeContext parentContext,
            SubagentScope scope) {
        if (managed.isReleasing()) {
            return Mono.error(new IllegalStateException("子代理正在释放，无法复用。"));
        }
        if (!managed.spawned.reusable()) {
            return Mono.error(new IllegalArgumentException("子代理不可复用：" + managed.spawned.key()));
        }
        return executeOrAccept(
                managed, request.task(), request.mode(), request.timeout(), parentContext, scope);
    }

    private ManagedSubagent create(
            Registry registry,
            String key,
            String agentId,
            String sessionId,
            String label,
            boolean reusable,
            Registration registration,
            RuntimeContext parentContext) {
        var labelError = claimLabel(registry, label, key);
        if (labelError != null) {
            throw labelError;
        }
        Agent agent;
        try {
            agent = registration.factory().create(parentContext);
        } catch (RuntimeException error) {
            releaseLabelIfUnused(registry, key, label);
            throw error;
        }
        if (agent == null) {
            releaseLabelIfUnused(registry, key, label);
            throw new IllegalStateException("子代理工厂返回了空实例。");
        }
        var managed = new ManagedSubagent(new SpawnedAgent(
                key, agentId, sessionId, label, reusable, agent));
        synchronized (registry) {
            var winner = registry.agents.putIfAbsent(key, managed);
            if (winner != null) {
                closeAgent(agent);
                return winner;
            }
        }
        return managed;
    }

    private Mono<SubagentTask> executeOrAccept(
            ManagedSubagent managed,
            String prompt,
            SubagentMode mode,
            Duration timeout,
            RuntimeContext parentContext,
            SubagentScope scope) {
        if (prompt == null || prompt.isBlank()) {
            return Mono.just(accepted(managed));
        }
        if (!managed.tryAcquire()) {
            return Mono.error(new IllegalStateException("子代理正在释放，无法执行。"));
        }
        var task = new MutableTask(
                "task-" + UUID.randomUUID(), managed.spawned, scope, mode == SubagentMode.BACKGROUND);
        tasks.put(task.taskId, task);
        var execution = execute(managed, task, prompt, parentContext, scope, timeout);
        if (mode == SubagentMode.BACKGROUND) {
            execution.subscribe();
            return Mono.just(task.snapshot());
        }
        return execution;
    }

    private Mono<SubagentTask> execute(
            ManagedSubagent managed,
            MutableTask task,
            String prompt,
            RuntimeContext parentContext,
            SubagentScope scope,
            Duration timeout) {
        AgentRun run;
        try {
            run = managed.spawned.agent().start(prompt, childContext(managed, parentContext));
        } catch (RuntimeException error) {
            return Mono.fromSupplier(() -> {
                task.fail(error);
                completeLease(managed, scope);
                return task.snapshot();
            });
        }
        forwardEvents(managed, run);
        var result = run.result()
                .map(agentResult -> {
                    task.complete(agentResult.content());
                    return task.snapshot();
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    task.fail(new IllegalStateException("子代理未返回结果。"));
                    return task.snapshot();
                }))
                .onErrorResume(error -> Mono.fromSupplier(() -> {
                    task.fail(error);
                    return task.snapshot();
                }))
                .doFinally(signal -> completeLease(managed, scope));
        task.running();
        if (task.background) {
            return result.doOnCancel(task::cancel);
        }
        var effectiveTimeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_TIMEOUT
                : timeout;
        return result
                .timeout(effectiveTimeout)
                .onErrorResume(error -> Mono.fromSupplier(() -> {
                    run.cancel();
                    task.fail(error);
                    return task.snapshot();
                }))
                .doOnCancel(task::cancel);
    }

    private void forwardEvents(ManagedSubagent managed, AgentRun run) {
        run.events()
                .doOnNext(event -> dispatch(managed.spawned.key(), event))
                .takeUntil(event -> event.type() == AgentEventType.AGENT_END)
                .subscribe();
    }

    private void dispatch(String agentKey, AgentEvent event) {
        try {
            eventListener.onEvent(agentKey, event);
        } catch (RuntimeException error) {
            log.warn("子代理事件监听器执行失败：{}", agentKey, error);
        }
    }

    private void completeLease(ManagedSubagent managed, SubagentScope scope) {
        managed.release();
        if (!managed.spawned.reusable()) {
            releaseOneShot(managed, scope);
        }
    }

    private void releaseOneShot(ManagedSubagent managed, SubagentScope scope) {
        var registry = registries.get(scope);
        if (registry == null) {
            return;
        }
        synchronized (registry) {
            if (!managed.beginRelease()) {
                return;
            }
            registry.agents.remove(managed.spawned.key(), managed);
            if (managed.spawned.label() != null) {
                registry.labelToKey.remove(lowerLabel(managed.spawned.label()), managed.spawned.key());
            }
        }
        closeAgent(managed.spawned.agent());
        removeRegistryIfEmpty(scope, registry);
    }

    private static SubagentTask accepted(ManagedSubagent managed) {
        var now = Instant.now();
        return new SubagentTask(
                null,
                managed.spawned.key(),
                managed.spawned.agentId(),
                managed.spawned.sessionId(),
                managed.spawned.label(),
                AgentRunStatus.CREATED,
                "",
                "",
                false,
                now,
                now);
    }

    private static RuntimeContext childContext(ManagedSubagent managed, RuntimeContext parent) {
        return RuntimeContext.builder()
                .userId(parent.userId())
                .sessionId(managed.spawned.sessionId())
                .metadata(parent.metadata())
                .build();
    }

    private static List<SubagentHandle> existingHandles(Registry registry) {
        synchronized (registry) {
            var handles = new ArrayList<SubagentHandle>();
            for (var managed : registry.agents.values()) {
                if (!managed.isReleasing()) {
                    handles.add(new SubagentHandle(
                            managed.spawned.key(),
                            managed.spawned.agentId(),
                            managed.spawned.sessionId(),
                            managed.spawned.label(),
                            managed.spawned.reusable(),
                            managed.inFlight() > 0));
                }
            }
            handles.sort(Comparator.comparing(SubagentHandle::agentKey));
            return List.copyOf(handles);
        }
    }

    private static ManagedSubagent existing(Registry registry, String key) {
        synchronized (registry) {
            return registry.agents.get(key);
        }
    }

    private static String labelKey(Registry registry, String label) {
        if (label == null) {
            return null;
        }
        synchronized (registry) {
            return registry.labelToKey.get(lowerLabel(label));
        }
    }

    private static IllegalArgumentException claimLabel(Registry registry, String label, String key) {
        if (label == null) {
            return null;
        }
        synchronized (registry) {
            var existing = registry.labelToKey.putIfAbsent(lowerLabel(label), key);
            if (existing != null && !existing.equals(key)) {
                return new IllegalArgumentException("子代理标签已被占用：" + label);
            }
            return null;
        }
    }

    private static void releaseLabelIfUnused(Registry registry, String key, String label) {
        if (label == null) {
            return;
        }
        synchronized (registry) {
            if (!registry.agents.containsKey(key)) {
                registry.labelToKey.remove(lowerLabel(label), key);
            }
        }
    }

    private void removeRegistryIfEmpty(SubagentScope scope, Registry registry) {
        synchronized (registry) {
            if (registry.agents.isEmpty() && registry.labelToKey.isEmpty()) {
                registries.remove(scope, registry);
            }
        }
    }

    private static void closeAgent(Agent agent) {
        if (agent instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception error) {
                log.warn("释放子代理失败：{}", agent.getClass().getName(), error);
            }
        }
    }

    private static String deterministicHash(String... values) {
        var joined = new StringBuilder();
        for (var value : values) {
            joined.append(value == null ? "" : value).append('\n');
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(joined.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 12);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 摘要算法不可用。", error);
        }
    }

    private static String lowerLabel(String label) {
        return label.toLowerCase(Locale.ROOT);
    }

    private static String resolvedReference(String agentKey, String label) {
        return agentKey != null && !agentKey.isBlank() ? agentKey.trim() : label;
    }

    private record Registration(boolean persistSession, SubagentFactory factory) {}

    private record SubagentScope(String userId, String sessionId) {

        private static SubagentScope from(RuntimeContext context) {
            return new SubagentScope(context.userId(), context.sessionId());
        }
    }

    private record SpawnedAgent(
            String key,
            String agentId,
            String sessionId,
            String label,
            boolean reusable,
            Agent agent) {}

    private static final class Registry {

        private final Map<String, ManagedSubagent> agents = new LinkedHashMap<>();

        private final Map<String, String> labelToKey = new LinkedHashMap<>();
    }

    private static final class ManagedSubagent {

        private final SpawnedAgent spawned;

        private boolean releasing;

        private int inFlight;

        private ManagedSubagent(SpawnedAgent spawned) {
            this.spawned = spawned;
        }

        private synchronized boolean tryAcquire() {
            if (releasing) {
                return false;
            }
            inFlight++;
            return true;
        }

        private synchronized boolean beginRelease() {
            if (releasing || inFlight != 0) {
                return false;
            }
            releasing = true;
            return true;
        }

        private synchronized void release() {
            if (inFlight > 0) {
                inFlight--;
            }
        }

        private synchronized boolean isReleasing() {
            return releasing;
        }

        private synchronized int inFlight() {
            return inFlight;
        }
    }

    private static final class MutableTask {

        private final String taskId;

        private final SpawnedAgent spawned;

        private final SubagentScope scope;

        private final boolean background;

        private final Instant createdAt = Instant.now();

        private final CompletableFuture<SubagentTask> completion = new CompletableFuture<>();

        private AgentRunStatus state = AgentRunStatus.CREATED;

        private String result = "";

        private String error = "";

        private Instant updatedAt = createdAt;

        private MutableTask(
                String taskId, SpawnedAgent spawned, SubagentScope scope, boolean background) {
            this.taskId = taskId;
            this.spawned = spawned;
            this.scope = scope;
            this.background = background;
        }

        private synchronized void running() {
            if (terminal()) {
                return;
            }
            state = AgentRunStatus.RUNNING;
            updatedAt = Instant.now();
        }

        private synchronized void complete(String output) {
            if (terminal()) {
                return;
            }
            state = AgentRunStatus.COMPLETED;
            result = output == null ? "" : output;
            updatedAt = Instant.now();
            completion.complete(snapshot());
        }

        private synchronized void fail(Throwable failure) {
            if (terminal()) {
                return;
            }
            state = AgentRunStatus.FAILED;
            error = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            updatedAt = Instant.now();
            completion.complete(snapshot());
        }

        private synchronized void cancel() {
            if (terminal()) {
                return;
            }
            state = AgentRunStatus.CANCELLED;
            updatedAt = Instant.now();
            completion.complete(snapshot());
        }

        private synchronized boolean terminal() {
            return state == AgentRunStatus.COMPLETED
                    || state == AgentRunStatus.FAILED
                    || state == AgentRunStatus.CANCELLED;
        }

        private Mono<SubagentTask> await() {
            return Mono.fromFuture(completion);
        }

        private synchronized SubagentTask snapshot() {
            return new SubagentTask(
                    taskId,
                    spawned.key(),
                    spawned.agentId(),
                    spawned.sessionId(),
                    spawned.label(),
                    state,
                    result,
                    error,
                    background,
                    createdAt,
                    updatedAt);
        }
    }
}