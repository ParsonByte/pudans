package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class SessionFilter {

    private final List<String> excludedPrefixes;

    public SessionFilter(Collection<String> excludedPrefixes) {
        Objects.requireNonNull(excludedPrefixes, "excludedPrefixes");
        this.excludedPrefixes = excludedPrefixes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .distinct()
                .toList();
    }

    public static SessionFilter fromSettings(AiSetting settings) {
        Objects.requireNonNull(settings, "settings");
        var memory = settings.getSpec() == null ? null : settings.getSpec().getMemory();
        return new SessionFilter(memory == null ? List.of() : memory.getExcludeSessionPrefixes());
    }

    public boolean shouldSkip(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return true;
        }
        return excludedPrefixes.stream().anyMatch(sessionId::startsWith);
    }

    public boolean shouldSkip(MemoryContext context) {
        return context == null || shouldSkip(context.sessionId());
    }
}
