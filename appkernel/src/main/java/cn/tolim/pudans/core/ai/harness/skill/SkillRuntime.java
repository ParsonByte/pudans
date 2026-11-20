package cn.tolim.pudans.core.ai.harness.skill;

import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public final class SkillRuntime {

    private static final Logger log = LoggerFactory.getLogger(SkillRuntime.class);

    private final List<SkillRepository> repositories;

    private final @Nullable SkillUsageStore usageStore;

    public SkillRuntime(List<SkillRepository> repositories) {
        this(repositories, null);
    }

    public SkillRuntime(List<SkillRepository> repositories, @Nullable SkillUsageStore usageStore) {
        if (repositories == null) {
            throw new IllegalArgumentException("技能仓库列表不能为空。");
        }
        this.repositories = List.copyOf(repositories);
        this.usageStore = usageStore;
    }

    public SkillCatalog catalog() {
        // 仓库按声明顺序生效，先命中的同名技能优先
        var indexed = new LinkedHashMap<String, SkillEntry>();
        for (var repository : repositories) {
            try {
                for (var entry : repository.list()) {
                    indexed.putIfAbsent(entry.name(), entry);
                }
            } catch (RuntimeException error) {
                log.warn("读取技能仓库 {} 失败：{}", repository.getClass().getSimpleName(), error.getMessage());
            }
        }
        return SkillCatalog.of(indexed.values());
    }

    public Optional<SkillEntry> load(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        for (var repository : repositories) {
            try {
                var entry = repository.find(name);
                if (entry.isPresent()) {
                    recordView(name);
                    return entry;
                }
            } catch (RuntimeException error) {
                log.warn("从技能仓库 {} 加载 {} 失败：{}", repository.getClass().getSimpleName(), name, error.getMessage());
            }
        }
        return Optional.empty();
    }

    public String prompt() {
        var catalog = catalog();
        if (catalog.isEmpty()) {
            return "";
        }
        var builder = new StringBuilder("## Available Skills\n\n<available_skills>\n");
        for (var entry : catalog.all()) {
            builder.append("<skill>\n");
            appendNode(builder, "name", entry.name());
            appendNode(builder, "description", entry.description());
            appendNode(builder, "source", entry.source());
            if (entry.filesRoot() != null) {
                appendNode(builder, "files-root", entry.filesRoot().toString());
            }
            builder.append("</skill>\n");
        }
        return builder.append("</available_skills>\n").toString();
    }

    private void recordView(String name) {
        if (usageStore == null) {
            return;
        }
        try {
            usageStore.recordView(name);
        } catch (RuntimeException error) {
            log.debug("记录技能 {} 使用情况失败：{}", name, error.getMessage());
        }
    }

    private static void appendNode(StringBuilder builder, String name, @Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        builder.append("  <").append(name).append('>')
                .append(escapeXml(value))
                .append("</").append(name).append(">\n");
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}