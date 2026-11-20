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
import cn.tolim.pudans.core.ai.harness.skill.ResourceSkillRepository;
import cn.tolim.pudans.core.ai.harness.skill.SkillRuntime;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageStore;
import cn.tolim.pudans.core.ai.harness.team.TeamRuntimeRegistry;
import cn.tolim.pudans.core.ai.harness.team.TeamTools;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultEvictionConfig;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceTools;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.command.SlashCommandService;
import cn.tolim.pudans.core.ai.library.environment.EnvironmentVariableService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.library.subagent.SubagentService;
import cn.tolim.pudans.core.ai.memory.MemoryTools;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import cn.tolim.pudans.core.ai.runtime.RuntimeTools;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 组装 Agent 关联资源，聊天与工具执行只依赖这一层。
@Component
@RequiredArgsConstructor
public class AiAgentContextService {

    private final LibraryView libraryView;

    private final SubagentService subagentService;

    private final SlashCommandService slashCommandService;

    private final EnvironmentVariableService environmentVariableService;

    private final SkillService skillService;

    private final SkillUsageStore skillUsageStore;

    private final TeamRuntimeRegistry teamRuntimeRegistry;

    public Mono<AgentContext> build(AiAgent agent) {
        var agentName = agent.getMetadata().getName();
        return Mono.zip(
                        libraryView.snapshot(agentName),
                        subagentService.byAgent(agentName).collectList(),
                        slashCommandService.byAgent(agentName).collectList(),
                        environmentVariableService.resolveValues(agentName),
                        skillService.byAgent(agentName).collectList())
                .map(tuple -> {
                    var settings = harness(agent);
                    return new AgentContext(
                            systemAdditions(tuple.getT1(), tuple.getT2()),
                            tools(agent, tuple.getT1(), settings),
                            tuple.getT1().skills(),
                            tuple.getT2(),
                            tuple.getT3(),
                            tuple.getT4(),
                            settings,
                            new SkillRuntime(
                                    List.of(new ResourceSkillRepository(tuple.getT5())), skillUsageStore),
                            teamTools(agent));
                });
    }

    private TeamTools teamTools(AiAgent agent) {
        var spec = agent == null ? null : agent.getSpec();
        if (spec == null || !StringUtils.hasText(spec.getTeam())) {
            return null;
        }
        return new TeamTools(teamRuntimeRegistry.runtime(spec.getTeam()));
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
                harness.isToolSearchEnabled(),
                harness.isToolGroupsEnabled(),
                harness.getAsyncToolPatterns(),
                harness.getExternalToolPatterns(),
                harness.isWorkspaceEnabled(),
                harness.isMemoryToolsEnabled(),
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
            AiAgent agent, LibraryView.Snapshot snapshot, HarnessSettings settings) {
        var tools = new ArrayList<AiTool>();
        for (var tool : snapshot.mcpTools().tools()) {
            tools.add(new AiTool(
                    tool.name(),
                    text(tool.description()),
                    tool.inputSchema() == null ? emptySchema() : tool.inputSchema(),
                    null));
        }
        if (hasRuntime(agent)) {
            tools.addAll(RuntimeTools.definitions());
        }
        if (settings.workspaceEnabled()) {
            tools.addAll(WorkspaceTools.definitions());
        }
        if (settings.memoryToolsEnabled()) {
            tools.addAll(MemoryTools.definitions());
        }
        return List.copyOf(tools);
    }

    private static boolean hasRuntime(AiAgent agent) {
        return agent.getSpec() != null
                && agent.getSpec().getRuntime() != null
                && agent.getSpec().getRuntime().getProvider() != AiAgent.RuntimeProvider.NONE;
    }

    private static JsonNode emptySchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        node.putObject("properties");
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
            HarnessSettings harness,
            SkillRuntime skillRuntime,
            @Nullable TeamTools teamTools) {

        public AgentContext {
            systemAdditions = systemAdditions == null ? "" : systemAdditions;
            tools = tools == null ? List.of() : List.copyOf(tools);
            skills = skills == null ? List.of() : List.copyOf(skills);
            subagents = subagents == null ? List.of() : List.copyOf(subagents);
            commands = commands == null ? List.of() : List.copyOf(commands);
            environment = environment == null ? Map.of() : Map.copyOf(environment);
            harness = harness == null ? HarnessSettings.DEFAULT : harness;
            skillRuntime = skillRuntime == null ? new SkillRuntime(List.of()) : skillRuntime;
        }

        public AgentContext(
                String systemAdditions,
                List<AiTool> tools,
                List<SkillService.SkillSummary> skills,
                List<AiSubagent> subagents,
                List<AiSlashCommand> commands,
                Map<String, String> environment) {
            this(
                    systemAdditions,
                    tools,
                    skills,
                    subagents,
                    commands,
                    environment,
                    HarnessSettings.DEFAULT,
                    new SkillRuntime(List.of()),
                    null);
        }
    }

    public record HarnessSettings(
            boolean planModeEnabled,
            boolean todosEnabled,
            CompactionConfig compaction,
            ToolResultEvictionConfig eviction,
            Duration confirmationTimeout,
            boolean toolSearchEnabled,
            boolean toolGroupsEnabled,
            List<String> asyncToolPatterns,
            List<String> externalToolPatterns,
            boolean workspaceEnabled,
            boolean memoryToolsEnabled,
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
                true,
                true,
                List.of(),
                List.of(),
                true,
                true,
                PermissionContext.bypass());

        public HarnessSettings {
            compaction = compaction == null ? CompactionConfig.disabled() : compaction;
            eviction = eviction == null ? ToolResultEvictionConfig.disabled() : eviction;
            confirmationTimeout = confirmationTimeout == null
                    ? Duration.ofSeconds(AiAgent.Harness.DEFAULT_CONFIRMATION_TIMEOUT_SECONDS)
                    : confirmationTimeout;
            asyncToolPatterns = asyncToolPatterns == null ? List.of() : List.copyOf(asyncToolPatterns);
            externalToolPatterns = externalToolPatterns == null
                    ? List.of()
                    : List.copyOf(externalToolPatterns);
            permission = permission == null ? PermissionContext.bypass() : permission;
        }
    }
}
