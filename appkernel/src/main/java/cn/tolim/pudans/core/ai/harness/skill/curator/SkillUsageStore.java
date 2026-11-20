package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.util.List;
import java.util.Optional;

public interface SkillUsageStore {

    Optional<SkillUsageRecord> get(String name);

    List<SkillUsageRecord> all();

    void recordView(String name);

    void recordUse(String name);

    void recordPatch(String name);

    void markPromoted(String name, String reviewerId, List<String> environments);

    void setState(String name, SkillUsageRecord.State state);
}