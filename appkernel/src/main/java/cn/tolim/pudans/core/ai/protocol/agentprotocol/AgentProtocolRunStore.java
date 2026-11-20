package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.infra.r2dbc.ai.protocol.AiAgentProtocolRunRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
public class AgentProtocolRunStore {

    private final ConcurrentHashMap<String, RunRecord> runs = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<ThreadKey, String> latestRuns = new ConcurrentHashMap<>();

    private final AiAgentProtocolRunRepository repository;

    @Autowired
    public AgentProtocolRunStore(AiAgentProtocolRunRepository repository) {
        this.repository = repository;
    }

    AgentProtocolRunStore() {
        this.repository = null;
    }

    Mono<RunRecord> create(
            String userId, String runId, String threadId, @Nullable String agentId, AgentRun run) {
        var record = new RunRecord(userId, runId, threadId, agentId, run);
        runs.put(runId, record);
        latestRuns.put(new ThreadKey(userId, threadId), runId);
        return persist(record).thenReturn(record);
    }

    public Mono<RunSnapshot> find(String userId, String runId) {
        var cached = owned(userId, runId);
        if (cached.isPresent()) {
            return Mono.just(cached.get().snapshot());
        }
        return findPersisted(userId, runId);
    }

    public Mono<RunSnapshot> latestByThread(String userId, String threadId) {
        var runId = latestRuns.get(new ThreadKey(userId, threadId));
        if (runId != null) {
            var cached = owned(userId, runId);
            if (cached.isPresent()) {
                return Mono.just(cached.get().snapshot());
            }
        }
        return repository == null
                ? Mono.empty()
                : repository.latestByThread(userId, threadId).map(AgentProtocolRunStore::snapshot);
    }

    public Mono<RunSnapshot> cancel(String userId, String runId) {
        var cached = owned(userId, runId);
        if (cached.isPresent()) {
            var record = cached.get();
            record.cancel();
            return persist(record).thenReturn(record.snapshot());
        }
        return findPersisted(userId, runId).flatMap(snapshot -> {
            if (snapshot.status().isTerminal()) {
                return Mono.just(snapshot);
            }
            var cancelled = new RunSnapshot(
                    snapshot.runId(),
                    snapshot.threadId(),
                    snapshot.agentId(),
                    AgentProtocolRunStatus.CANCELLED,
                    snapshot.content(),
                    snapshot.error());
            return repository == null
                    ? Mono.just(cancelled)
                    : repository
                            .upsert(
                                    userId,
                                    runId,
                                    snapshot.threadId(),
                                    snapshot.agentId(),
                                    cancelled.status().name(),
                                    cancelled.content(),
                                    cancelled.error())
                            .thenReturn(cancelled);
        });
    }

    Mono<Void> complete(String runId, String content) {
        var record = runs.get(runId);
        if (record == null) {
            return Mono.empty();
        }
        record.complete(content);
        return persist(record);
    }

    Mono<Void> fail(String runId, String error) {
        var record = runs.get(runId);
        if (record == null) {
            return Mono.empty();
        }
        record.fail(error);
        return persist(record);
    }

    private Mono<Void> persist(RunRecord record) {
        if (repository == null) {
            return Mono.empty();
        }
        var snapshot = record.snapshot();
        return repository.upsert(
                record.userId(),
                record.runId(),
                record.threadId(),
                record.agentId(),
                snapshot.status().name(),
                snapshot.content(),
                snapshot.error());
    }

    private Mono<RunSnapshot> findPersisted(String userId, String runId) {
        return repository == null
                ? Mono.empty()
                : repository.find(userId, runId).map(AgentProtocolRunStore::snapshot);
    }

    private static RunSnapshot snapshot(AiAgentProtocolRunRepository.RunRow row) {
        return new RunSnapshot(
                row.runId(),
                row.threadId(),
                StringUtils.hasText(row.agentId()) ? row.agentId() : null,
                AgentProtocolRunStatus.valueOf(row.status()),
                StringUtils.hasText(row.content()) ? row.content() : null,
                StringUtils.hasText(row.error()) ? row.error() : null);
    }

    private Optional<RunRecord> owned(String userId, String runId) {
        var record = runs.get(runId);
        return record != null && record.userId().equals(userId) ? Optional.of(record) : Optional.empty();
    }

    public record RunSnapshot(
            String runId,
            String threadId,
            @Nullable String agentId,
            AgentProtocolRunStatus status,
            @Nullable String content,
            @Nullable String error) {}

    static final class RunRecord {

        private final String userId;

        private final String runId;

        private final String threadId;

        private final @Nullable String agentId;

        private final AgentRun run;

        private final AtomicReference<AgentProtocolRunStatus> status =
                new AtomicReference<>(AgentProtocolRunStatus.PENDING);

        private final AtomicReference<String> content = new AtomicReference<>();

        private final AtomicReference<String> error = new AtomicReference<>();

        RunRecord(String userId, String runId, String threadId, @Nullable String agentId, AgentRun run) {
            this.userId = userId;
            this.runId = runId;
            this.threadId = threadId;
            this.agentId = agentId;
            this.run = run;
        }

        String userId() {
            return userId;
        }

        String runId() {
            return runId;
        }

        String threadId() {
            return threadId;
        }

        @Nullable String agentId() {
            return agentId;
        }

        AgentRun run() {
            return run;
        }

        void markRunning() {
            status.compareAndSet(AgentProtocolRunStatus.PENDING, AgentProtocolRunStatus.RUNNING);
        }

        synchronized void complete(String value) {
            if (status.get().isTerminal()) {
                return;
            }
            content.set(value);
            status.set(AgentProtocolRunStatus.COMPLETED);
        }

        synchronized void fail(String message) {
            if (status.get().isTerminal()) {
                return;
            }
            error.set(message);
            status.set(AgentProtocolRunStatus.FAILED);
        }

        void cancel() {
            if (status.get().isTerminal()) {
                return;
            }
            synchronized (this) {
                if (status.get().isTerminal()) {
                    return;
                }
                run.cancel();
                status.set(AgentProtocolRunStatus.CANCELLED);
            }
        }

        RunSnapshot snapshot() {
            return new RunSnapshot(runId, threadId, agentId, status.get(), content.get(), error.get());
        }
    }

    private record ThreadKey(String userId, String threadId) {}
}