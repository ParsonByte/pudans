package cn.tolim.pudans.core.ai.harness.team;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TeamTask(
        String taskId,
        String teamName,
        String subject,
        String description,
        TeamTaskState state,
        String owner,
        List<String> blockedBy,
        String result,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public TeamTask {
        taskId = Objects.requireNonNull(taskId, "任务标识不能为空");
        teamName = Objects.requireNonNull(teamName, "团队名称不能为空");
        subject = subject == null ? "" : subject;
        description = description == null ? "" : description;
        state = Objects.requireNonNull(state, "任务状态不能为空");
        owner = owner == null ? "" : owner;
        blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
        result = result == null ? "" : result;
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    TeamTask next(TeamTaskState nextState, String nextOwner, String nextResult) {
        return new TeamTask(
                taskId,
                teamName,
                subject,
                description,
                nextState,
                nextOwner,
                blockedBy,
                nextResult,
                version + 1,
                createdAt,
                Instant.now());
    }
}