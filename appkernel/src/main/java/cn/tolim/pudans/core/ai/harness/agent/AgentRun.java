package cn.tolim.pudans.core.ai.harness.agent;

import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class AgentRun {

    private final String id;

    private final AtomicReference<AgentRunStatus> status =
            new AtomicReference<>(AgentRunStatus.CREATED);

    private final AtomicBoolean cancelled = new AtomicBoolean();

    private final Sinks.Many<AgentEvent> events = Sinks.many().replay().limit(256);

    private final Map<String, Sinks.One<Boolean>> pendingConfirmations = new ConcurrentHashMap<>();

    private final Map<String, Boolean> confirmations = new ConcurrentHashMap<>();

    private volatile Consumer<AgentEvent> eventListener = event -> {};

    private Mono<AgentResult> result;

    public AgentRun(String id) {
        this.id = Objects.requireNonNull(id, "运行标识不能为空");
    }

    public String id() {
        return id;
    }

    public AgentRunStatus status() {
        return status.get();
    }

    public boolean cancelled() {
        return cancelled.get();
    }

    public Mono<AgentResult> result() {
        if (result == null) {
            throw new IllegalStateException("AgentRun 尚未绑定执行结果。");
        }
        return result
                .doOnSubscribe(subscription -> status.compareAndSet(AgentRunStatus.CREATED, AgentRunStatus.RUNNING))
                .doOnNext(value -> status.set(AgentRunStatus.COMPLETED))
                .doOnError(error -> {
                    if (cancelled.get()) {
                        status.set(AgentRunStatus.CANCELLED);
                    } else {
                        status.compareAndSet(AgentRunStatus.RUNNING, AgentRunStatus.FAILED);
                    }
                })
                .doOnCancel(() -> {
                    cancelled.set(true);
                    status.set(AgentRunStatus.CANCELLED);
                });
    }

    public Flux<AgentEvent> events() {
        return events.asFlux();
    }

    public void onEvent(Consumer<AgentEvent> listener) {
        eventListener = Objects.requireNonNull(listener, "Agent 事件监听器不能为空");
    }

    public void emit(AgentEvent event) {
        var value = Objects.requireNonNull(event, "Agent 事件不能为空");
        events.tryEmitNext(value);
        eventListener.accept(value);
    }

    public Mono<Boolean> awaitConfirmation(String toolCallId) {
        var decided = confirmations.remove(toolCallId);
        if (decided != null) {
            emit(new AgentEvent.UserConfirmResult(id, toolCallId, decided));
            return Mono.just(decided);
        }
        Sinks.One<Boolean> sink = Sinks.one();
        pendingConfirmations.put(toolCallId, sink);
        status.set(AgentRunStatus.SUSPENDED);
        return sink.asMono()
                .doOnNext(value -> {
                    if (!cancelled.get()) {
                        status.set(AgentRunStatus.RUNNING);
                    }
                })
                .doFinally(signal -> pendingConfirmations.remove(toolCallId));
    }

    public boolean confirm(String toolCallId, boolean approved) {
        var sink = pendingConfirmations.remove(toolCallId);
        if (sink != null) {
            emit(new AgentEvent.UserConfirmResult(id, toolCallId, approved));
            return sink.tryEmitValue(approved).isSuccess();
        }
        confirmations.put(toolCallId, approved);
        return true;
    }

    public void attach(Mono<AgentResult> result) {
        if (this.result != null) {
            throw new IllegalStateException("AgentRun 已绑定执行结果。");
        }
        this.result = Objects.requireNonNull(result, "执行结果不能为空");
    }

    public void cancel() {
        cancelled.set(true);
        status.set(AgentRunStatus.CANCELLED);
        pendingConfirmations.values().forEach(sink -> sink.tryEmitValue(false));
        pendingConfirmations.clear();
    }

    public void interrupt() {
        cancel();
    }
}