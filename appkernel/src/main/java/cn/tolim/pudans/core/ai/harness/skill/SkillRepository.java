package cn.tolim.pudans.core.ai.harness.skill;

import java.util.List;
import java.util.Optional;

public interface SkillRepository {

    List<SkillEntry> list();

    default Optional<SkillEntry> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return list().stream().filter(entry -> name.equals(entry.name())).findFirst();
    }

    default void save(SkillEntry entry) {
        throw new UnsupportedOperationException("技能仓库是只读的。");
    }

    default boolean archive(String name) {
        throw new UnsupportedOperationException("技能仓库是只读的。");
    }
}