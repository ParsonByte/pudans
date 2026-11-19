package cn.tolim.pudans.core.ai.gateway.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AiAgentAdapterRegistry {

    private final Map<String, AiAgentAdapter> adapters;

    private final AiAgentAdapter fallback;

    public AiAgentAdapterRegistry(List<AiAgentAdapter> adapters, AiAgentAdapter fallback) {
        this.fallback = Objects.requireNonNull(fallback, "默认 Agent 适配器不能为空");
        var bySource = new HashMap<String, AiAgentAdapter>();
        for (var adapter : Objects.requireNonNull(adapters, "Agent 适配器列表不能为空")) {
            var source = normalize(Objects.requireNonNull(adapter.source(), "Agent 适配器来源不能为空"));
            if (bySource.putIfAbsent(source, adapter) != null) {
                throw new IllegalArgumentException("重复的 Agent 适配器: " + source);
            }
        }
        this.adapters = Map.copyOf(bySource);
    }

    public AiAgentAdapter resolve(String source) {
        if (source == null) {
            return fallback;
        }
        return adapters.getOrDefault(normalize(source), fallback);
    }

    private static String normalize(String source) {
        return source.toLowerCase(Locale.ROOT);
    }
}
