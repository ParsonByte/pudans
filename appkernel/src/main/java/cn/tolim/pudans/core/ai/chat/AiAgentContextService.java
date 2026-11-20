package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiSlashCommand;
import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.harness.compaction.CompactionConfig;
import cn.tolim.pudans.core.ai.harness.permission.PermissionBehavior;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionMode;
import cn.tolim.pudans.core.ai.harness.permission.PermissionRule;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultEvictionConfig;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.command.SlashCommandService;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariableService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.library.subagent.SubagentService;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 组装 Agent 关联资源，聊天与工具执行只依赖这一层。
@Component
@RequiredArgsConstructor
public class AiAgentContextService {

    private static final String TOOL_SKILL = "skill";

    private static final String TOOL_SUBAGENT = "subagent";

    private static final String TOOL_RUNTIME_EXEC = "runtime_exec";

    private final LibraryView libraryView;

    private final SubagentService subagentService;

    private final SlashCommandService slashCommandService;

    private final EnvironmentVariableService environmentVariableService;

    public Mono<AgentContext> build(AiAgent agent) {
        var agentName = agent.getMetadata().getName();
        return Mono.zip(
                        libraryView.snapshot(agentName),
                        subagentService.byAgent(agentName).collectList(),
                        slashCommandService.byAgent(agentName).collectList(),
                        environmentVariableService.resolveValues(agentName))
                .map(tuple -> new AgentContext(
                        systemAdditions(tuple.getT1(), tuple.getT2()),
                        tools(agent, tuple.getT1(), tuple.getT2()),
                        tuple.getT1().skills(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        harness(agent)));
    }

    private static HarnessSettings harness(AiAgent agent) {
        var spec = agent == null ? null : agent.getSpec();
        var harness = spec == null || spec.getHarness() == null ? new AiAgent.Harness() : spec.getHarness();
        var permissions = PermissionContext.builder()
                .mode(harness.getPermissionMode() == null
                        ? PermissionMode.BYPASS
                        : PermissionMode.valueOf(harness.getPermissionMode().name()));
        var rules = harness.getPermissionRules() == null
                ? List.<AiAgent.Harness.PermissionRule>of()
                : harness.getPermissionRules();
        for (var rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getToolName())) {
                continue;
            }
            var behavior = rule.getBehavior() == null
                    ? AiAgent.Harness.PermissionBehavior.ASK
                    : rule.getBehavior();
            permissions.rule(new PermissionRule(
                    rule.getToolName(),
                    rule.getRuleContent(),
                    PermissionBehavior.valueOf(behavior.name()),
                    "agent"));
        }
        return new HarnessSettings(
                harness.isPlanModeEnabled(),
                harness.isTodosEnabled(),
                new CompactionConfig(
                        harness.getCompactionTriggerMessages(), harness.getCompactionKeepMessages()),
                new ToolResultEvictionConfig(harness.getToolResultEvictionMaxChars()),
                Duration.ofSeconds(Math.max(1, harness.getConfirmationTimeoutSeconds())),
                permissions.build());
    }

    public String systemMessage(AgentContext context) {
        return context == null ? "" : context.systemAdditions();
    }

    private static String systemAdditions(LibraryView.Snapshot snapshot, List<AiSubagent> subagents) {
        var sections = new ArrayList<String>();
        if (StringUtils.hasText(snapshot.instructions())) {
            sections.add(ChatPrompts.INSTRUCTION_HEADER + snapshot.instructions());
        }
        if (!snapshot.skills().isEmpty()) {
            var lines = snapshot.skills().stream()
                    .map(skill -> "- " + displayName(skill.name(), skill.displayName()) + "："
                            + text(skill.description()))
                    .toList();
            sections.add(ChatPrompts.SKILLS_HEADER + String.join("\n", lines)
                    + ChatPrompts.SKILLS_HINT);
        }
        if (!subagents.isEmpty()) {
            var lines = subagents.stream()
                    .map(subagent -> "- " + displayName(
                                    subagent.getMetadata().getName(),
                                    subagent.getSpec() == null ? null : subagent.getSpec().getDisplayName())
                            + "：" + text(subagent.getSpec() == null ? null : subagent.getSpec().getDescription()))
                    .toList();
            sections.add(ChatPrompts.SUBAGENTS_HEADER + String.join("\n", lines)
                    + ChatPrompts.SUBAGENTS_HINT);
        }
        return String.join("\n\n", sections);
    }

    private static List<AiTool> tools(
            AiAgent agent, LibraryView.Snapshot snapshot, List<AiSubagent> subagents) {
        var tools = new ArrayList<AiTool>();
        for (var tool : snapshot.mcpTools().tools()) {
            tools.add(new AiTool(
                    tool.name(),
                    text(tool.description()),
                    tool.inputSchema() == null ? emptySchema() : tool.inputSchema(),
                    null));
        }
        if (!snapshot.skills().isEmpty()) {
            tools.add(new AiTool(
                    TOOL_SKILL,
                    "读取指定技能的完整内容。",
                    objectSchema(Map.of("name", "技能名称"), List.of("name")),
                    null));
        }
        if (!subagents.isEmpty()) {
            tools.add(new AiTool(
                    TOOL_SUBAGENT,
                    "将任务委派给指定子代理并返回其回答。",
                    objectSchema(
                            Map.of("name", "子代理名称", "prompt", "委派给子代理的任务描述"),
                            List.of("name", "prompt")),
                    null));
        }
        if (hasRuntime(agent)) {
            tools.add(new AiTool(
                    TOOL_RUNTIME_EXEC,
                    "在 Agent 沙箱中执行命令并返回退出码与输出。",
                    runtimeSchema(),
                    null));
        }
        return List.copyOf(tools);
    }

    private static boolean hasRuntime(AiAgent agent) {
        return agent.getSpec() != null
                && agent.getSpec().getRuntime() != null
                && agent.getSpec().getRuntime().getProvider() != AiAgent.RuntimeProvider.NONE;
    }

    private static JsonNode runtimeSchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        var properties = node.putObject("properties");
        var command = properties.putObject("command");
        command.put("type", "string");
        command.put("description", "要执行的命令");
        var args = properties.putObject("args");
        args.put("type", "array");
        args.putObject("items").put("type", "string");
        args.put("description", "命令参数");
        var workdir = properties.putObject("workdir");
        workdir.put("type", "string");
        workdir.put("description", "工作目录");
        var timeout = properties.putObject("timeoutSeconds");
        timeout.put("type", "integer");
        timeout.put("description", "超时秒数");
        var required = node.putArray("required");
        required.add("command");
        return node;
    }

    private static JsonNode emptySchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        node.putObject("properties");
        return node;
    }

    private static JsonNode objectSchema(Map<String, String> properties, List<String> required) {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        ObjectNode propertyNode = node.putObject("properties");
        properties.forEach((name, description) -> {
            var property = propertyNode.putObject(name);
            property.put("type", "string");
            property.put("description", description);
        });
        var requiredNode = node.putArray("required");
        required.forEach(requiredNode::add);
        return node;
    }

    private static String displayName(String name, String displayName) {
        return StringUtils.hasText(displayName) ? displayName : text(name);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public record AgentContext(
            String systemAdditions,
            List<AiTool> tools,
            List<SkillService.SkillSummary> skills,
            List<AiSubagent> subagents,
            List<AiSlashCommand> commands,
            Map<String, String> environment,
            HarnessSettings harness) {

        public AgentContext {
            systemAdditions = systemAdditions == null ? "" : systemAdditions;
            tools = tools == null ? List.of() : List.copyOf(tools);
            skills = skills == null ? List.of() : List.copyOf(skills);
            subagents = subagents == null ? List.of() : List.copyOf(subagents);
            commands = commands == null ? List.of() : List.copyOf(commands);
            environment = environment == null ? Map.of() : Map.copyOf(environment);
            harness = harness == null ? HarnessSettings.DEFAULT : harness;
        }

        public AgentContext(
                String systemAdditions,
                List<AiTool> tools,
                List<SkillService.SkillSummary> skills,
                List<AiSubagent> subagents,
                List<AiSlashCommand> commands,
                Map<String, String> environment) {
            this(systemAdditions, tools, skills, subagents, commands, environment, HarnessSettings.DEFAULT);
        }
    }

    public record HarnessSettings(
            boolean planModeEnabled,
            boolean todosEnabled,
            CompactionConfig compaction,
            ToolResultEvictionConfig eviction,
            Duration confirmationTimeout,
            PermissionContext permission) {

        public static final HarnessSettings DEFAULT = new HarnessSettings(
                false,
                true,
                new CompactionConfig(
                        AiAgent.Harness.DEFAULT_COMPACTION_TRIGGER_MESSAGES,
                        AiAgent.Harness.DEFAULT_COMPACTION_KEEP_MESSAGES),
                new ToolResultEvictionConfig(
                        AiAgent.Harness.DEFAULT_TOOL_RESULT_EVICTION_MAX_CHARS),
                Duration.ofSeconds(AiAgent.Harness.DEFAULT_CONFIRMATION_TIMEOUT_SECONDS),
                PermissionContext.bypass());

        public HarnessSettings {
            compaction = compaction == null ? CompactionConfig.disabled() : compaction;
            eviction = eviction == null ? ToolResultEvictionConfig.disabled() : eviction;
            confirmationTimeout = confirmationTimeout == null
                    ? Duration.ofSeconds(AiAgent.Harness.DEFAULT_CONFIRMATION_TIMEOUT_SECONDS)
                    : confirmationTimeout;
            permission = permission == null ? PermissionContext.bypass() : permission;
        }
    }
}
