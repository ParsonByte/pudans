package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemorySkillAuditLog implements SkillAuditLog {

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(Entry entry) {
        entries.add(Objects.requireNonNull(entry, "审计记录不能为空。"));
    }

    @Override
    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}