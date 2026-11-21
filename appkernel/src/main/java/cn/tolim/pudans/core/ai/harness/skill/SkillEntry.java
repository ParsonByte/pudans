package cn.tolim.pudans.core.ai.harness.skill;

import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.contract.extension.Metadata;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record SkillEntry(
        AiSkill skill, String source, SkillResources resources, @Nullable Path filesRoot) {

    public SkillEntry {
        if (skill == null || skill.getSpec() == null) {
            throw new IllegalArgumentException("技能内容不能为空。");
        }
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("技能来源不能为空。");
        }
        if (skill.getMetadata() == null || !StringUtils.hasText(skill.getMetadata().getName())) {
            throw new IllegalArgumentException("技能名称不能为空。");
        }
        resources = resources == null ? SkillResources.empty() : resources;
        filesRoot = filesRoot == null ? null : filesRoot.toAbsolutePath().normalize();
    }

    public String name() {
        return skill.getMetadata().getName();
    }

    public @Nullable String description() {
        return skill.getSpec().getDescription();
    }

    public String markdown() {
        var content = skill.getSpec().getContent();
        if (StringUtils.hasText(content)) {
            return content;
        }
        var builder = new StringBuilder("---\nname: ").append(scalar(name())).append('\n');
        if (StringUtils.hasText(description())) {
            builder.append("description: ").append(scalar(description())).append('\n');
        }
        return builder.append("---\n").toString();
    }

    public static SkillEntry fromMarkdown(
            @Nullable String content,
            String fallbackName,
            String source,
            SkillResources resources,
            @Nullable Path filesRoot) {
        var markdown = content == null ? "" : content;
        var parsed = parseFrontmatter(markdown);
        var name = parsed != null && StringUtils.hasText(parsed.name())
                ? parsed.name()
                : SkillFiles.validateName(fallbackName);
        var spec = new AiSkill.Spec();
        spec.setDisplayName(name);
        spec.setDescription(parsed == null ? null : parsed.description());
        spec.setSource(AiSkill.Source.INLINE);
        spec.setContent(markdown);
        var skill = new AiSkill();
        skill.setSpec(spec);
        var metadata = new Metadata();
        metadata.setName(name);
        skill.setMetadata(metadata);
        return new SkillEntry(skill, source, resources, filesRoot);
    }

    private static @Nullable Frontmatter parseFrontmatter(String content) {
        var lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        var start = 0;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }
        if (start >= lines.length || !"---".equals(lines[start].strip())) {
            return null;
        }
        String name = null;
        String description = null;
        for (var index = start + 1; index < lines.length; index++) {
            var line = lines[index].strip();
            if ("---".equals(line)) {
                return new Frontmatter(name, description);
            }
            var colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            var key = line.substring(0, colon).strip();
            var value = unquote(line.substring(colon + 1).strip());
            if ("name".equals(key)) {
                name = value;
            } else if ("description".equals(key)) {
                description = value;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            var first = value.charAt(0);
            var last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String scalar(String value) {
        var plain = value.indexOf(':') < 0
                && value.indexOf('#') < 0
                && value.indexOf('"') < 0
                && !value.startsWith(" ")
                && !value.endsWith(" ");
        if (plain) {
            return value;
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Frontmatter(@Nullable String name, @Nullable String description) {}
}