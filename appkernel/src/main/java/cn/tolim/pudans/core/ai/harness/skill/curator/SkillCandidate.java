package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record SkillCandidate(
        String name, @Nullable String description, String content, List<String> supportFiles) {

    public SkillCandidate {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("候选技能名称不能为空。");
        }
        content = content == null ? "" : content;
        supportFiles = supportFiles == null ? List.of() : List.copyOf(supportFiles);
    }
}