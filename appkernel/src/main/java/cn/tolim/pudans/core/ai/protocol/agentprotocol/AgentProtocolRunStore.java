package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AgentProtocolRunStore {

    private final ConcurrentHashMap<String, RunRecord> runs = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<ThreadKey, String> latestRuns = new ConcurrentHashMap<>();

    RunRecord create(String userId, String runId, String threadId, @Nullable String agentId, AgentRun run) {
        var record = new RunRecord(userId, runId, threadId, agentId, run);
        runs.put(runId, record);
        latestRuns.put(new ThreadKey(userId, threadId), runId);
        return record;
    }

    public Optional<RunSnapshot> find(String userId, String runId) {
        return owned(userId, runId).map(RunRecord::snapshot);
    }

    public Optional<RunSnapshot> latestByThread(String userId, String threadId) {
        var runId = latestRuns.get(new ThreadKey(userId, threadId));
        return runId == null ? Optional.empty() : find(userId, runId);
    }

    public Optional<RunSnapshot> cancel(String userId, String runId) {
        return owned(userId, runId).map(record -> {
            record.cancel();
            return record.snapshot();
        });
    }

    void complete(String runId, String content) {
        var record = runs.get(runId);
        if (record != null) {
            record.complete(content);
        }
    }

    void fail(String runId, String error) {
        var record = runs.get(runId);
        if (record != null) {
            record.fail(error);
        }
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