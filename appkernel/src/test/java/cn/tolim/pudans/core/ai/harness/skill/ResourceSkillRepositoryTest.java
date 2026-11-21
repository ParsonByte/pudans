package cn.tolim.pudans.core.ai.harness.skill;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.extension.api.Metadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceSkillRepositoryTest {

    @Test
    void exposesResourceSkillsAsCatalogEntries() {
        var repository = new ResourceSkillRepository(List.of(
                skill("release", "release notes"),
                skill("review", "review changes")));

        assertThat(repository.list()).extracting(SkillEntry::name)
                .containsExactly("release", "review");
        var entry = repository.find("release").orElseThrow();
        assertThat(entry.source()).isEqualTo("resource");
        assertThat(entry.description()).isEqualTo("release notes");
        assertThat(entry.markdown()).isEqualTo("# release");
    }

    @Test
    void ignoresSkillsWithoutMetadata() {
        var skill = new AiSkill();
        skill.getSpec().setDisplayName("broken");

        assertThat(new ResourceSkillRepository(List.of(skill)).list()).isEmpty();
    }

    private static AiSkill skill(String name, String description) {
        var skill = new AiSkill();
        skill.getSpec().setDisplayName(name);
        skill.getSpec().setDescription(description);
        skill.getSpec().setContent("# " + name);
        var metadata = new Metadata();
        metadata.setName(name);
        skill.setMetadata(metadata);
        return skill;
    }
}