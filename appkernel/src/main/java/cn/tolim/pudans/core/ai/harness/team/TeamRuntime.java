package cn.tolim.pudans.core.ai.harness.team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

public final class TeamRuntime {

    public static final String LEAD = "lead";

    public static final int DEFAULT_MESSAGE_LIMIT = 50;

    private final String teamName;

    private final TeamAccessPolicy accessPolicy;

    private final Board board = new Board();

    private final @Nullable TeamTaskStore taskStore;

    public TeamRuntime(String teamName, TeamAccessPolicy accessPolicy) {
        this(teamName, accessPolicy, null);
    }

    public TeamRuntime(
            String teamName, TeamAccessPolicy accessPolicy, @Nullable TeamTaskStore taskStore) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("团队名称不能为空。");
        }
        this.teamName = teamName.trim();
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "团队访问策略不能为空");
        this.taskStore = taskStore;
    }

    public Mono<TeamTask> createTask(
            String userId,
            String subject,
            String description,
            List<String> blockedBy,
            String owner) {
        return authorize(userId).then(taskStore != null
                ? taskStore.create(teamName, subject, description, blockedBy, owner)
                : Mono.fromCallable(() -> board.createTask(teamName, subject, description, blockedBy, owner)));
    }

    public Mono<TeamTask> assignTask(
            String userId, String taskId, String owner, long expectedVersion) {
        return authorize(userId).then(taskStore != null
                ? taskStore.assign(teamName, taskId, owner, expectedVersion)
                : Mono.fromCallable(() -> board.assignTask(taskId, owner, expectedVersion)));
    }

    public Mono<TeamTask> claimTask(
            String userId, String memberName, String taskId, long expectedVersion) {
        return authorize(userId).then(taskStore != null
                ? taskStore.claim(teamName, taskId, memberName, expectedVersion)
                : Mono.fromCallable(() -> board.claimTask(taskId, memberName, expectedVersion)));
    }

    public Mono<TeamTask> completeTask(String userId, String taskId, String result) {
        return authorize(userId).then(taskStore != null
                ? taskStore.complete(teamName, taskId, result)
                : Mono.fromCallable(() -> board.completeTask(taskId, result)));
    }

    public Mono<TeamTask> failTask(String userId, String taskId, String reason) {
        return authorize(userId).then(taskStore != null
                ? taskStore.fail(teamName, taskId, reason)
                : Mono.fromCallable(() -> board.failTask(taskId, reason)));
    }

    public Mono<List<TeamTask>> tasks(String userId) {
        return authorize(userId).then(taskStore != null
                ? taskStore.tasks(teamName)
                : Mono.fromCallable(() -> board.tasks()));
    }

    public Mono<List<TeamTask>> claimableTasks(String userId, String memberName) {
        return authorize(userId).then(taskStore != null
                ? taskStore.claimable(teamName, memberName)
                : Mono.fromCallable(() -> board.claimableTasks(memberName)));
    }

    public Mono<TeamMessage> sendMessage(
            String userId, String from, String to, String content) {
        return authorize(userId).then(Mono.fromCallable(
                () -> board.sendMessage(from, to, content)));
    }

    public Mono<List<TeamMessage>> broadcastMessage(String userId, String from, String content) {
        return authorize(userId).then(Mono.fromCallable(
                () -> board.broadcastMessage(from, content)));
    }

    public Mono<List<TeamMessage>> inbox(String userId, String memberName, int limit) {
        return authorize(userId).then(Mono.fromCallable(
                () -> board.inbox(memberName, limit)));
    }

    public Mono<List<TeamMember>> members(String userId) {
        return authorize(userId).then(Mono.fromCallable(() -> board.members()));
    }

    public Mono<TeamMember> addMember(
            String userId, String name, String agentRef, boolean lead) {
        return authorize(userId).then(Mono.fromCallable(
                () -> board.addMember(name, agentRef, lead)));
    }

    public Mono<Void> removeMember(String userId, String name) {
        return authorize(userId).then(Mono.fromRunnable(() -> board.removeMember(name)));
    }

    private Mono<Void> authorize(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new IllegalArgumentException("用户标识不能为空。"));
        }
        return accessPolicy.requireMember(teamName, userId);
    }

    private static final class Board {

        private final Map<String, TeamTask> tasks = new LinkedHashMap<>();

        private final Map<String, TeamMember> members = new LinkedHashMap<>();

        private final List<TeamMessage> messages = new ArrayList<>();

        private long taskSequence;

        private long messageSequence;

        private synchronized TeamTask createTask(
                String teamName,
                String subject,
                String description,
                List<String> blockedBy,
                String owner) {
            requireText(subject, "任务标题");
            var dependencies = normalizeDependencies(blockedBy);
            for (var dependency : dependencies) {
                requireTask(dependency);
            }
            var now = Instant.now();
            var task = new TeamTask(
                    "task-" + (++taskSequence),
                    teamName,
                    subject.trim(),
                    normalize(description),
                    TeamTaskState.PENDING,
                    normalize(owner),
                    dependencies,
                    "",
                    1,
                    now,
                    now);
            tasks.put(task.taskId(), task);
            notifyAssigned(task);
            return task;
        }

        private synchronized TeamTask assignTask(
                String taskId, String owner, long expectedVersion) {
            requireText(owner, "负责人");
            var current = requireTask(taskId);
            if (current.state() != TeamTaskState.PENDING) {
                throw new TeamConflictException("任务不在待处理状态：" + current.state());
            }
            checkVersion(current, expectedVersion);
            var next = current.next(TeamTaskState.PENDING, owner.trim(), current.result());
            tasks.put(taskId, next);
            notifyAssigned(next);
            return next;
        }

        private synchronized TeamTask claimTask(
                String taskId, String memberName, long expectedVersion) {
            requireText(memberName, "领取人");
            var member = memberName.trim();
            var current = requireTask(taskId);
            if (current.state() == TeamTaskState.IN_PROGRESS && member.equals(current.owner())) {
                return current;
            }
            checkVersion(current, expectedVersion);
            if (current.state() != TeamTaskState.PENDING) {
                throw new TeamConflictException("任务不在待领取状态：" + current.state());
            }
            if (!current.owner().isBlank() && !current.owner().equals(member)) {
                throw new TeamConflictException("任务已分配给：" + current.owner());
            }
            if (isBlocked(current)) {
                throw new TeamConflictException("任务仍被前置任务阻塞。");
            }
            var next = current.next(TeamTaskState.IN_PROGRESS, member, current.result());
            tasks.put(taskId, next);
            return next;
        }

        private synchronized TeamTask completeTask(String taskId, String result) {
            var current = requireTask(taskId);
            if (current.state() != TeamTaskState.IN_PROGRESS) {
                throw new TeamConflictException("任务不在进行中状态：" + current.state());
            }
            var next = current.next(TeamTaskState.COMPLETED, current.owner(), normalize(result));
            tasks.put(taskId, next);
            notifySettled(next, "completed");
            return next;
        }

        private synchronized TeamTask failTask(String taskId, String reason) {
            var current = requireTask(taskId);
            if (current.state().terminal()) {
                throw new TeamConflictException("任务已处于终态：" + current.state());
            }
            var next = current.next(TeamTaskState.FAILED, current.owner(), normalize(reason));
            tasks.put(taskId, next);
            notifySettled(next, "failed");
            return next;
        }

        private synchronized List<TeamTask> tasks() {
            return List.copyOf(tasks.values());
        }

        private synchronized List<TeamTask> claimableTasks(String memberName) {
            var member = normalize(memberName);
            var claimable = new ArrayList<TeamTask>();
            for (var task : tasks.values()) {
                if (task.state() != TeamTaskState.PENDING) {
                    continue;
                }
                if (!task.owner().isBlank() && (member.isBlank() || !task.owner().equals(member))) {
                    continue;
                }
                if (!isBlocked(task)) {
                    claimable.add(task);
                }
            }
            return List.copyOf(claimable);
        }

        private synchronized TeamMessage sendMessage(String from, String to, String content) {
            requireText(from, "发送人");
            requireText(to, "接收人");
            requireText(content, "消息内容");
            if (from.trim().equals(to.trim())) {
                throw new IllegalArgumentException("不能给自己发送团队消息。");
            }
            return addMessage(from.trim(), to.trim(), content);
        }

        private synchronized List<TeamMessage> broadcastMessage(String from, String content) {
            requireText(from, "发送人");
            requireText(content, "消息内容");
            var sender = from.trim();
            var sent = new ArrayList<TeamMessage>();
            for (var member : members.keySet()) {
                if (!member.equals(sender)) {
                    sent.add(addMessage(sender, member, content));
                }
            }
            return List.copyOf(sent);
        }

        private synchronized List<TeamMessage> inbox(String memberName, int limit) {
            requireText(memberName, "成员名称");
            var member = memberName.trim();
            var effectiveLimit = limit <= 0 ? DEFAULT_MESSAGE_LIMIT : limit;
            var received = new ArrayList<TeamMessage>();
            for (var message : messages) {
                if (member.equals(message.to())) {
                    received.add(message);
                }
            }
            if (received.size() > effectiveLimit) {
                return List.copyOf(
                        received.subList(received.size() - effectiveLimit, received.size()));
            }
            return List.copyOf(received);
        }

        private synchronized List<TeamMember> members() {
            return List.copyOf(members.values());
        }

        private synchronized TeamMember addMember(
                String name, String agentRef, boolean lead) {
            requireText(name, "成员名称");
            var memberName = name.trim();
            if (lead) {
                for (var entry : members.entrySet()) {
                    var member = entry.getValue();
                    if (member.lead() && !entry.getKey().equals(memberName)) {
                        entry.setValue(new TeamMember(entry.getKey(), member.agentRef(), false));
                    }
                }
            }
            var member = new TeamMember(memberName, normalize(agentRef), lead);
            members.put(memberName, member);
            return member;
        }

        private synchronized void removeMember(String name) {
            requireText(name, "成员名称");
            if (members.remove(name.trim()) == null) {
                throw new IllegalArgumentException("团队成员不存在：" + name);
            }
        }

        private boolean isBlocked(TeamTask task) {
            for (var dependency : task.blockedBy()) {
                var blocking = tasks.get(dependency);
                if (blocking == null || blocking.state() != TeamTaskState.COMPLETED) {
                    return true;
                }
            }
            return false;
        }

        private void notifyAssigned(TeamTask task) {
            var owner = task.owner();
            if (owner.isBlank() || LEAD.equals(owner)) {
                return;
            }
            var content = new StringBuilder("[team] Task ")
                    .append(task.taskId())
                    .append(" (")
                    .append(task.subject())
                    .append(") was assigned to you.\n");
            if (!task.description().isBlank()) {
                content.append("\nDescription:\n").append(task.description()).append('\n');
            }
            content.append("\nCall claimTask with task_id=")
                    .append(task.taskId())
                    .append(" to start, then completeTask with a result or failTask with a reason.");
            addMessage(LEAD, owner, content.toString());
        }

        private void notifySettled(TeamTask task, String verb) {
            var owner = task.owner().isBlank() ? "unknown" : task.owner();
            if (LEAD.equals(owner)) {
                return;
            }
            var label = "failed".equals(verb) ? "Reason" : "Result";
            var detail = task.result().isBlank() ? "(no detail provided)" : task.result();
            var content = "[team] Task "
                    + task.taskId()
                    + " ("
                    + task.subject()
                    + ") "
                    + verb
                    + " by "
                    + owner
                    + ".\n\n"
                    + label
                    + ":\n"
                    + detail;
            addMessage(owner, LEAD, content);
        }

        private TeamMessage addMessage(String from, String to, String content) {
            var message = new TeamMessage(++messageSequence, from, to, content, Instant.now());
            messages.add(message);
            return message;
        }

        private TeamTask requireTask(String taskId) {
            var task = taskId == null ? null : tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("团队任务不存在：" + taskId);
            }
            return task;
        }

        private static void checkVersion(TeamTask task, long expectedVersion) {
            if (expectedVersion > 0 && expectedVersion != task.version()) {
                throw new TeamConflictException(
                        "任务版本冲突：期望 " + expectedVersion + "，实际 " + task.version());
            }
        }

        private static List<String> normalizeDependencies(List<String> blockedBy) {
            if (blockedBy == null || blockedBy.isEmpty()) {
                return List.of();
            }
            var dependencies = new LinkedHashSet<String>();
            for (var dependency : blockedBy) {
                if (dependency != null && !dependency.isBlank()) {
                    dependencies.add(dependency.trim());
                }
            }
            return List.copyOf(dependencies);
        }

        private static void requireText(String value, String label) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + "不能为空。");
            }
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}