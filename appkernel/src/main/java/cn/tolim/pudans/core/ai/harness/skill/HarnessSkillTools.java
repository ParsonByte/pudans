package cn.tolim.pudans.core.ai.harness.skill;

import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.Objects;

public final class HarnessSkillTools {

    public static final String SKILL = "skill";

    private HarnessSkillTools() {}

    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(SKILL)
                .description("读取指定技能的 SKILL.md 内容。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "技能名称")
                        .required("name")
                        .build())
                .build();
    }

    public static HarnessToolkit.ToolResult execute(
            JsonNode arguments, SkillRuntime runtime) {
        if (runtime == null) {
            return new HarnessToolkit.ToolResult("技能运行时未配置。", true);
        }
        var name = arguments == null ? "" : arguments.path("name").asText("");
        if (name.isBlank()) {
            return new HarnessToolkit.ToolResult("缺少技能名称。", true);
        }
        var entry = runtime.load(name);
        if (entry.isEmpty()) {
            return new HarnessToolkit.ToolResult("技能不存在：" + name, true);
        }
        var skill = entry.get();
        var output = new StringBuilder(skill.markdown());
        if (!skill.resources().list().isEmpty()) {
            output.append("\n\n资源：\n");
            skill.resources().list().forEach(file -> output.append("- ").append(file).append('\n'));
        }
        return new HarnessToolkit.ToolResult(output.toString(), false);
    }
}