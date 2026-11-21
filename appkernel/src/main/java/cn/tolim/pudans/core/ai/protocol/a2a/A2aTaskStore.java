package cn.tolim.pudans.core.ai.protocol.a2a;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.infra.r2dbc.ai.protocol.AiA2aTaskRepository;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class A2aTaskStore {

    private final ConcurrentMap<String, A2aTaskRecord> tasks = new ConcurrentHashMap<>();

    private final AiA2aTaskRepository repository;

    @Autowired
    public A2aTaskStore(AiA2aTaskRepository repository) {
        this.repository = repository;
    }

    A2aTaskStore() {
        this.repository = null;
    }

    public Mono<Void> save(A2aTaskRecord record) {
        tasks.put(record.taskId(), record);
        return repository == null ? Mono.empty() : repository.upsert(record);
    }

    public Mono<A2aTaskRecord> find(String userId, String taskId) {
        var cached = tasks.get(taskId);
        if (cached != null && cached.userId().equals(userId)) {
            return Mono.just(cached);
        }
        if (repository == null) {
            return Mono.empty();
        }
        return repository
                .find(userId, taskId)
                .map(row -> new A2aTaskRecord(
                        userId,
                        taskId,
                        row.contextId(),
                        new A2aTaskStatus(row.status()),
                        artifact(row.artifactJson(), taskId)));
    }

    private static A2aArtifact artifact(String json, String taskId) {
        if (json == null || json.isBlank() || "null".equals(json.strip())) {
            return null;
        }
        try {
            return JsonUtils.mapper().readValue(json, A2aArtifact.class);
        } catch (Exception error) {
            log.warn("A2A 任务 {} 的产物无法解析。", taskId, error);
            return null;
        }
    }
}