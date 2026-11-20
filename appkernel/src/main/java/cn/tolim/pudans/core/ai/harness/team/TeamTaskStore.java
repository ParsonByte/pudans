package cn.tolim.pudans.core.ai.harness.team;

import java.util.List;
import reactor.core.publisher.Mono;

// 团队任务板存储；内存实现保留给测试，生产使用数据库实现
public interface TeamTaskStore {

    Mono<TeamTask> create(
            String teamName, String subject, String description, List<String> blockedBy, String owner);

    Mono<TeamTask> assign(String teamName, String taskId, String owner, long expectedVersion);

    Mono<TeamTask> claim(String teamName, String taskId, String memberName, long expectedVersion);

    Mono<TeamTask> complete(String teamName, String taskId, String result);

    Mono<TeamTask> fail(String teamName, String taskId, String reason);

    Mono<List<TeamTask>> tasks(String teamName);

    Mono<List<TeamTask>> claimable(String teamName, String memberName);
}