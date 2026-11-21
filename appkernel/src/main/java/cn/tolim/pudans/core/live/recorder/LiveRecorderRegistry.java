package cn.tolim.pudans.core.live.recorder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 本实例内的录制任务与准入计数，供停止、防重与指标使用
@Component
@RequiredArgsConstructor
public class LiveRecorderRegistry {

    private final LiveRecorderAdmission admission;

    private final Map<String, LiveRecorderTask> tasks = new ConcurrentHashMap<>();

    public void register(String recordingName, LiveRecorderTask task) {
        tasks.put(recordingName, task);
    }

    public void unregister(String recordingName) {
        tasks.remove(recordingName);
    }

    public boolean isActive(String recordingName) {
        return tasks.containsKey(recordingName);
    }

    public int activeCount() {
        return admission.activeCount();
    }

    public int queuedCount() {
        return admission.queuedCount();
    }

    public Mono<Void> requestStop(String recordingName) {
        var task = tasks.get(recordingName);
        if (task == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(task::requestStop);
    }

    public boolean isStreaming(String recordingName) {
        var task = tasks.get(recordingName);
        return task != null && task.isStreaming();
    }

    public Mono<Void> forceStop(String recordingName) {
        var task = tasks.get(recordingName);
        if (task == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(task::forceClose);
    }
}
