package cn.tolim.pudans.core.ai.protocol.a2a;

import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

// 任务状态与产物由运行结果并发更新，所有读写都在实例锁内完成。
public final class A2aTaskRecord {

    private final String userId;

    private final String taskId;

    private final String contextId;

    private @Nullable AgentRun run;

    private @Nullable A2aArtifact artifact;

    private A2aTaskStatus status = new A2aTaskStatus(A2aTaskStatus.SUBMITTED);

    public A2aTaskRecord(String userId, String taskId, String contextId) {
        this(userId, taskId, contextId, new A2aTaskStatus(A2aTaskStatus.SUBMITTED), null);
    }

    public A2aTaskRecord(
            String userId,
            String taskId,
            String contextId,
            A2aTaskStatus status,
            @Nullable A2aArtifact artifact) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户标识不能为空。");
        }
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务标识不能为空。");
        }
        if (!StringUtils.hasText(contextId)) {
            throw new IllegalArgumentException("上下文标识不能为空。");
        }
        this.userId = userId;
        this.taskId = taskId;
        this.contextId = contextId;
        this.status = Objects.requireNonNull(status, "任务状态不能为空");
        this.artifact = artifact;
    }

    public String userId() {
        return userId;
    }

    public String taskId() {
        return taskId;
    }

    public String contextId() {
        return contextId;
    }

    public synchronized A2aTaskStatus status() {
        return status;
    }

    public synchronized @Nullable AgentRun run() {
        return run;
    }

    public synchronized @Nullable A2aArtifact artifact() {
        return artifact;
    }

    public synchronized void attach(AgentRun run) {
        if (this.run != null) {
            throw new IllegalStateException("任务已绑定运行句柄。");
        }
        this.run = Objects.requireNonNull(run, "运行句柄不能为空");
    }

    public synchronized void working() {
        if (A2aTaskStatus.SUBMITTED.equals(status.state())) {
            status = new A2aTaskStatus(A2aTaskStatus.WORKING);
        }
    }

    public synchronized void complete(A2aArtifact artifact) {
        if (status.terminal()) {
            return;
        }
        this.artifact = Objects.requireNonNull(artifact, "产物不能为空");
        status = new A2aTaskStatus(A2aTaskStatus.COMPLETED);
    }

    public synchronized void fail() {
        if (!status.terminal()) {
            status = new A2aTaskStatus(A2aTaskStatus.FAILED);
        }
    }

    public synchronized boolean cancel() {
        if (status.terminal()) {
            return false;
        }
        status = new A2aTaskStatus(A2aTaskStatus.CANCELED);
        return true;
    }

    public synchronized A2aTask task() {
        return A2aTask.of(taskId, contextId, status, artifact == null ? List.of() : List.of(artifact));
    }
}