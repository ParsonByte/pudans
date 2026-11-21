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

    // 登记本实例持有的录制任务
    public void register(String recordingName, LiveRecorderTask task) {
        tasks.put(recordingName, task);
    }

    // 移除录制任务，重复移除无副作用
    public void unregister(String recordingName) {
        tasks.remove(recordingName);
    }

    // 任务仍在本实例内存中即视为活动
    public boolean isActive(String recordingName) {
        return tasks.containsKey(recordingName);
    }

    // 返回正在占用槽位的录制数
    public int activeCount() {
        return admission.activeCount();
    }

    // 返回仍在等待槽位的录制数
    public int queuedCount() {
        return admission.queuedCount();
    }

    // 本实例持有任务时请求其停止
    public Mono<Void> requestStop(String recordingName) {
        var task = tasks.get(recordingName);
        if (task == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(task::requestStop);
    }

    // 本实例任务仍在拉流时返回真
    public boolean isStreaming(String recordingName) {
        var task = tasks.get(recordingName);
        return task != null && task.isStreaming();
    }

    // 强制关闭本实例任务的拉流器
    public Mono<Void> forceStop(String recordingName) {
        var task = tasks.get(recordingName);
        if (task == null) {
            return Mono.empty();
        }
        return Mono.fromRunnable(task::forceClose);
    }
}
