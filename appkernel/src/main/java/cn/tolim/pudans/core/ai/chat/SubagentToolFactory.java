package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiSubagent;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
import cn.tolim.pudans.core.ai.agent.GatewayChatModel;
import cn.tolim.pudans.core.ai.agent.GatewayRunState;
import cn.tolim.pudans.core.ai.agent.GatewayStreamingChatModel;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.HarnessAgent;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.compaction.CompactionConfig;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.skill.ResourceSkillRepository;
import cn.tolim.pudans.core.ai.harness.skill.SkillRuntime;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageStore;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentRuntime;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultEvictionConfig;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.library.skill.Skills;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SubagentToolFactory {

    private static final String AGENT_SOURCE = "pudans-subagent";

    private final AiGateway gatewayService;

    private final AgentStateStore stateStore;

    private final ThreadJournalStore journalStore;

    private final Skills skillService;

    private final SkillUsageStore skillUsageStore;

    private final McpServers mcpServers;

    public Mono<SubagentTools> create(
            GatewayChatContext gateway, List<AiSubagent> subagents) {
        var valid = (subagents == null ? List.<AiSubagent>of() : subagents).stream()
                .filter(SubagentToolFactory::valid)
                .toList();
        if (valid.isEmpty()) {
            return Mono.just(new SubagentTools(new SubagentRuntime()));
        }
        var skillNames = valid.stream()
                .flatMap(subagent -> strings(subagent.getSpec().getSkills()).stream())
                .distinct()
                .toList();
        var serverNames = valid.stream()
                .flatMap(subagent -> strings(subagent.getSpec().getMcpServers()).stream())
                .distinct()
                .toList();
        return Mono.zip(
                        skillService.byNames(skillNames).defaultIfEmpty(List.of()),
                        mcpServers
                                .toolCatalogFor(serverNames)
                                .defaultIfEmpty(new McpServers.McpToolCatalog(List.of())))
                .map(tuple -> {
                    var skills = tuple.getT1().stream()
                            .filter(skill -> skill.getMetadata() != null)
                            .collect(Collectors.toMap(
                                    skill -> skill.getMetadata().getName(),
                                    Function.identity(),
                                    (first, second) -> first));
                    var definitions = tuple.getT2().tools().stream()
                            .collect(Collectors.groupingBy(
                                    McpServers.McpToolDefinition::server));
                    var runtime = new SubagentRuntime();
                    for (var subagent : valid) {
                        var name = subagent.getMetadata().getName();
                        var spec = subagent.getSpec();
                        var skillRuntime = new SkillRuntime(
                                List.of(new ResourceSkillRepository(strings(spec.getSkills()).stream()
                                        .map(skills::get)
                                        .filter(Objects::nonNull)
                                        .toList())),
                                skillUsageStore);
                        var toolkit = toolkit(definitions, strings(spec.getMcpServers()));
                        runtime.register(
                                name,
                                parentContext -> build(
                                        subagent, gateway, toolkit, skillRuntime, parentContext));
                    }
                    return new SubagentTools(runtime);
                });
    }

    private HarnessToolkit toolkit(
            Map<String, List<McpServers.McpToolDefinition>> definitions,
            List<String> serverNames) {
        var toolkit = new HarnessToolkit();
        for (var server : serverNames) {
            for (var definition : definitions.getOrDefault(server, List.of())) {
                var schema = definition.inputSchema() == null ? emptySchema() : definition.inputSchema();
                toolkit.register(
                        new AiTool(
                                definition.name(),
                                text(definition.description()),
                                schema,
                                null),
                        (request, arguments) ->
                                executeMcp(serverNames, definition.name(), arguments));
            }
        }
        return toolkit;
    }

    private HarnessToolkit.ToolResult executeMcp(
            List<String> serverNames, String toolName, JsonNode arguments) {
        try {
            var result = mcpServers.callToolFor(serverNames, toolName, arguments).block();
            if (result == null) {
                return new HarnessToolkit.ToolResult("MCP 工具未返回结果。", true);
            }
            return new HarnessToolkit.ToolResult(result.output(), result.isError());
        } catch (RuntimeException error) {
            return new HarnessToolkit.ToolResult(message(error), true);
        }
    }

    private HarnessAgent build(
            AiSubagent subagent,
            GatewayChatContext gateway,
            HarnessToolkit toolkit,
            SkillRuntime skillRuntime,
            RuntimeContext parentContext) {
        var spec = subagent.getSpec();
        var requestId = "sub-" + UUID.randomUUID();
        var context = new GatewayChatContext(
                parentContext.userId(),
                AGENT_SOURCE,
                parentContext.sessionId(),
                null,
                requestId,
                requestId,
                null,
                StringUtils.hasText(spec.getRouteGroup())
                        ? spec.getRouteGroup()
                        : gateway.routeGroupCode(),
                subagent.getMetadata().getName(),
                null,
                StringUtils.hasText(spec.getModel()) ? spec.getModel() : gateway.modelOverride());
        var runState = new GatewayRunState();
        var harness = new AiAgent.Harness();
        return HarnessAgent.builder()
                .name("subagent-" + subagent.getMetadata().getName())
                .systemPrompt(spec.getPrompt())
                .chatModel(new GatewayChatModel(gatewayService, context, runState))
                .streamingChatModel(new GatewayStreamingChatModel(gatewayService, context, runState))
                .toolkit(toolkit)
                .stateStore(stateStore)
                .journalStore(journalStore)
                .skillRuntime(skillRuntime)
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .compaction(new CompactionConfig(
                        harness.getCompactionTriggerMessages(), harness.getCompactionKeepMessages()))
                .toolResultEviction(new ToolResultEvictionConfig(
                        harness.getToolResultEvictionMaxChars()))
                .confirmationTimeout(Duration.ofSeconds(harness.getConfirmationTimeoutSeconds()))
                .build();
    }

    private static boolean valid(AiSubagent subagent) {
        return subagent != null
                && subagent.getMetadata() != null
                && StringUtils.hasText(subagent.getMetadata().getName())
                && subagent.getSpec() != null
                && StringUtils.hasText(subagent.getSpec().getPrompt());
    }

    private static List<String> strings(List<String> values) {
        return values == null
                ? List.of()
                : values.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    private static JsonNode emptySchema() {
        var node = JsonUtils.mapper().createObjectNode();
        node.put("type", "object");
        node.putObject("properties");
        return node;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String message(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? message : "子代理工具执行失败。";
    }
}