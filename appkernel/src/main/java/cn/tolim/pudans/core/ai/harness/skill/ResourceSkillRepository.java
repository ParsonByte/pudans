package cn.tolim.pudans.core.ai.harness.skill;

import cn.tolim.pudans.contract.core.extension.ai.AiSkill;
import java.util.List;
import org.springframework.util.StringUtils;

public final class ResourceSkillRepository implements SkillRepository {

    private final List<SkillEntry> entries;

    public ResourceSkillRepository(List<AiSkill> skills) {
        this.entries = (skills == null ? List.<AiSkill>of() : skills).stream()
                .filter(ResourceSkillRepository::valid)
                .map(skill -> new SkillEntry(skill, "resource", SkillResources.empty(), null))
                .toList();
    }

    @Override
    public List<SkillEntry> list() {
        return entries;
    }

    private static boolean valid(AiSkill skill) {
        return skill != null
                && skill.getSpec() != null
                && skill.getMetadata() != null
                && StringUtils.hasText(skill.getMetadata().getName());
    }
}