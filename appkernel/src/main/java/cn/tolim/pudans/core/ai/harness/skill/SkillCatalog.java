package cn.tolim.pudans.core.ai.harness.skill;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkillCatalog {

    private final Map<String, SkillEntry> entries;

    private SkillCatalog(Map<String, SkillEntry> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static SkillCatalog empty() {
        return new SkillCatalog(Map.of());
    }

    public static SkillCatalog of(Collection<SkillEntry> entries) {
        var indexed = new LinkedHashMap<String, SkillEntry>();
        if (entries != null) {
            for (var entry : entries) {
                if (entry != null) {
                    indexed.putIfAbsent(entry.name(), entry);
                }
            }
        }
        return indexed.isEmpty() ? empty() : new SkillCatalog(indexed);
    }

    public Optional<SkillEntry> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(name));
    }

    public Collection<SkillEntry> all() {
        return entries.values();
    }

    public List<String> names() {
        return List.copyOf(entries.keySet());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }
}