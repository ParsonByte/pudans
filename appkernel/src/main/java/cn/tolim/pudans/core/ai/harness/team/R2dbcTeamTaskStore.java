package cn.tolim.pudans.core.ai.harness.team;

import cn.tolim.pudans.infra.r2dbc.ai.team.AiTeamTaskRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 任务板使用数据库保存，跨副本通过版本号避免覆盖并发更新
@Component
@RequiredArgsConstructor
public class R2dbcTeamTaskStore implements TeamTaskStore {

    private final AiTeamTaskRepository repository;

    @Override
    public Mono<TeamTask> create(
            String teamName, String subject, String description, List<String> blockedBy, String owner) {
        if (!StringUtils.hasText(subject)) {
            return Mono.error(new IllegalArgumentException("任务标题不能为空。"));
        }
        var dependencies = normalizeDependencies(blockedBy);
        var ownerName = StringUtils.hasText(owner) ? owner.trim() : "";
        var existingDependencies = dependencies.isEmpty()
                ? Mono.just(0L)
                : repository.countExisting(teamName, dependencies);
        return existingDependencies
                .flatMap(existing -> existing == dependencies.size()
                        ? repository.insert(new TeamTask(
                                "task-" + UUID.randomUUID(),
                                teamName,
                                subject.trim(),
                                description == null ? "" : description,
                                TeamTaskState.PENDING,
                                ownerName,
                                dependencies,
                                "",
                                1,
                                Instant.now(),
                                Instant.now()))
                        : Mono.error(new IllegalArgumentException("前置任务不存在。")));
    }

    @Override
    public Mono<TeamTask> assign(String teamName, String taskId, String owner, long expectedVersion) {
        if (!StringUtils.hasText(owner)) {
            return Mono.error(new IllegalArgumentException("负责人不能为空。"));
        }
        var ownerName = owner.trim();
        return repository
                .find(teamName, taskId)
                .switchIfEmpty(Mono.error(new TeamConflictException("任务不存在：" + taskId)))
                .flatMap(current -> {
                    if (current.state() != TeamTaskState.PENDING) {
                        return Mono.error(new TeamConflictException("任务不在待处理状态：" + current.state()));
                    }
                    checkVersion(current, expectedVersion);
                    return update(teamName, taskId, expectedVersion, TeamTaskState.PENDING, ownerName, current.result());
                });
    }

    @Override
    public Mono<TeamTask> claim(
            String teamName, String taskId, String memberName, long expectedVersion) {
        if (!StringUtils.hasText(memberName)) {
            return Mono.error(new IllegalArgumentException("领取人不能为空。"));
        }
        var member = memberName.trim();
        return repository
                .find(teamName, taskId)
                .switchIfEmpty(Mono.error(new TeamConflictException("任务不存在：" + taskId)))
                .flatMap(current -> {
                    if (current.state() == TeamTaskState.IN_PROGRESS && member.equals(current.owner())) {
                        return Mono.just(current);
                    }
                    checkVersion(current, expectedVersion);
                    if (current.state() != TeamTaskState.PENDING) {
                        return Mono.error(new TeamConflictException("任务不在待领取状态：" + current.state()));
                    }
                    if (!current.owner().isBlank() && !current.owner().equals(member)) {
                        return Mono.error(new TeamConflictException("任务已分配给：" + current.owner()));
                    }
                    if (current.blockedBy().isEmpty()) {
                        return update(
                                teamName,
                                taskId,
                                expectedVersion,
                                TeamTaskState.IN_PROGRESS,
                                member,
                                current.result());
                    }
                    return repository
                            .unfinishedDependencies(teamName, current.blockedBy())
                            .flatMap(unfinished -> unfinished > 0
                                    ? Mono.error(new TeamConflictException("任务仍被前置任务阻塞。"))
                                    : update(
                                            teamName,
                                            taskId,
                                            expectedVersion,
                                            TeamTaskState.IN_PROGRESS,
                                            member,
                                            current.result()));
                });
    }

    @Override
    public Mono<TeamTask> complete(String teamName, String taskId, String result) {
        return repository
                .find(teamName, taskId)
                .switchIfEmpty(Mono.error(new TeamConflictException("任务不存在：" + taskId)))
                .flatMap(current -> current.state() == TeamTaskState.IN_PROGRESS
                        ? update(
                                teamName,
                                taskId,
                                current.version(),
                                TeamTaskState.COMPLETED,
                                current.owner(),
                                result)
                        : Mono.error(new TeamConflictException("任务不在进行中状态：" + current.state())));
    }

    @Override
    public Mono<TeamTask> fail(String teamName, String taskId, String reason) {
        return repository
                .find(teamName, taskId)
                .switchIfEmpty(Mono.error(new TeamConflictException("任务不存在：" + taskId)))
                .flatMap(current -> {
                    if (current.state().terminal()) {
                        return Mono.error(new TeamConflictException("任务已结束：" + current.state()));
                    }
                    return update(
                            teamName,
                            taskId,
                            current.version(),
                            TeamTaskState.FAILED,
                            current.owner(),
                            reason);
                });
    }

    @Override
    public Mono<List<TeamTask>> tasks(String teamName) {
        return repository.list(teamName).collectList();
    }

    @Override
    public Mono<List<TeamTask>> claimable(String teamName, String memberName) {
        return repository.claimable(teamName, memberName).collectList();
    }

    private Mono<TeamTask> update(
            String teamName,
            String taskId,
            long expectedVersion,
            TeamTaskState state,
            String owner,
            String result) {
        return repository
                .update(teamName, taskId, expectedVersion, state, owner, result)
                .switchIfEmpty(Mono.error(new TeamConflictException("任务版本已变化，请刷新后重试。")));
    }

    private static void checkVersion(TeamTask task, long expectedVersion) {
        if (task.version() != expectedVersion) {
            throw new TeamConflictException("任务版本已变化，请刷新后重试。");
        }
    }

    private static List<String> normalizeDependencies(List<String> blockedBy) {
        if (blockedBy == null || blockedBy.isEmpty()) {
            return List.of();
        }
        var unique = new LinkedHashSet<String>();
        for (var dependency : blockedBy) {
            if (StringUtils.hasText(dependency)) {
                unique.add(dependency.trim());
            }
        }
        return List.copyOf(unique);
    }
}