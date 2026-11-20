package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

public final class InMemorySkillUsageStore implements SkillUsageStore {

    private final ConcurrentMap<String, SkillUsageRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<SkillUsageRecord> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(name));
    }

    @Override
    public List<SkillUsageRecord> all() {
        return records.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public void recordView(String name) {
        var now = Instant.now();
        update(name, record -> record.view(now));
    }

    @Override
    public void recordUse(String name) {
        var now = Instant.now();
        update(name, record -> record.use(now));
    }

    @Override
    public void recordPatch(String name) {
        var now = Instant.now();
        update(name, record -> record.patch(now));
    }

    @Override
    public void markPromoted(String name, String reviewerId, List<String> environments) {
        var now = Instant.now();
        update(name, record -> record.promoted(reviewerId, environments, now));
    }

    @Override
    public void setState(String name, SkillUsageRecord.State state) {
        if (state == null) {
            throw new IllegalArgumentException("技能状态不能为空。");
        }
        update(name, record -> record.withState(state));
    }

    private SkillUsageRecord update(String name, UnaryOperator<SkillUsageRecord> change) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("技能名称不能为空。");
        }
        return records.compute(
                name, (key, current) -> change.apply(current == null ? SkillUsageRecord.defaults() : current));
    }
}