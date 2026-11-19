package cn.tolim.pudans.core.ai.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AiTaskHandlerRegistry {

    private final Map<String, AiTaskHandler> handlers;

    public AiTaskHandlerRegistry(List<AiTaskHandler> handlers) {
        var byType = new HashMap<String, AiTaskHandler>();
        for (var handler : Objects.requireNonNull(handlers, "任务处理器列表不能为空")) {
            var type = Objects.requireNonNull(handler.taskType(), "任务处理器类型不能为空");
            if (byType.putIfAbsent(type, handler) != null) {
                throw new IllegalArgumentException("重复的 AI 任务处理器: " + type);
            }
        }
        this.handlers = Map.copyOf(byType);
    }

    public AiTaskHandler find(String taskType) {
        return taskType == null ? null : handlers.get(taskType);
    }
}
