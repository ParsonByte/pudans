package cn.tolim.pudans.core.ai.protocol.a2a;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class A2aTaskStore {

    private final ConcurrentMap<String, A2aTaskRecord> tasks = new ConcurrentHashMap<>();

    public void save(A2aTaskRecord record) {
        tasks.put(record.taskId(), record);
    }

    public Optional<A2aTaskRecord> find(String userId, String taskId) {
        var record = tasks.get(taskId);
        return record != null && record.userId().equals(userId) ? Optional.of(record) : Optional.empty();
    }
}