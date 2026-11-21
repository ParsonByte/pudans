package cn.tolim.pudans.core.ai.protocol;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
import cn.tolim.pudans.core.ai.agent.GatewayChatModel;
import cn.tolim.pudans.core.ai.agent.GatewayRunState;
import cn.tolim.pudans.core.ai.agent.GatewayStreamingChatModel;
import cn.tolim.pudans.core.ai.chat.AgentContext;
import cn.tolim.pudans.core.ai.chat.ChatTools;
import cn.tolim.pudans.core.ai.chat.SubagentToolFactory;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.HarnessAgent;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.observability.HarnessObservability;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkitFactory;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import cn.tolim.pudans.core.ai.team.Teams;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 为 A2A、AG-UI 与 Agent Protocol 提供统一的 Harness 运行入口。
@Component
@RequiredArgsConstructor
public class ProtocolAgentRuntime {

    private static final String AGENT_SOURCE = "pudans-protocol";

    private final Teams teams;

    private final AgentContext agentContext;

    private final ChatTools chatTools;

    private final SubagentToolFactory subagentToolFactory;

    private final HarnessToolkitFactory harnessToolkitFactory;

    private final McpServers mcpServers;

    private final HarnessObservability harnessObservability;

    private final AiGateway aiGateway;

    private final AgentStateStore stateStore;

    private final ThreadJournalStore journalStore;

    public Mono<AgentRun> start(ProtocolRunRequest request) {
        var agentName = StringUtils.hasText(request.agentName())
                ? request.agentName()
                : Teams.DEFAULT_AGENT;
        return teams
                .requireAgent(agentName, request.userId())
                .flatMap(agent -> agentContext.build(agent).flatMap(context -> {
                    var state = new GatewayRunState();
                    var gatewayContext = gatewayContext(request, agent);
                    var toolkit = toolkit(request.userId(), agent, context);
                    return subagentToolFactory
                            .create(gatewayContext, context.subagents())
                            .flatMap(subagentTools -> mcpServers
                                    .decorateToolkit(toolkit, agent.getMetadata().getName())
                                    .map(decorated -> HarnessAgent.builder()
                                            .name(agent.getMetadata().getName())
                                            .systemPrompt(systemPrompt(request, agent, context))
                                            .chatModel(new GatewayChatModel(
                                                    aiGateway, gatewayContext, state))
                                            .streamingChatModel(new GatewayStreamingChatModel(
                                                    aiGateway, gatewayContext, state))
                                            .toolkit(decorated)
                                            .stateStore(stateStore)
                                            .journalStore(journalStore)
                                            .permissionEngine(new PermissionEngine(
                                                    context.harness().permission()))
                                            .confirmationTimeout(
                                                    context.harness().confirmationTimeout())
                                            .skillRuntime(context.skillRuntime())
                                            .subagentTools(subagentTools)
                                            .teamTools(context.teamTools())
                                            .planModeEnabled(
                                                    context.harness().planModeEnabled())
                                            .todoEnabled(context.harness().todosEnabled())
                                            .toolSearchEnabled(
                                                    context.harness().toolSearchEnabled())
                                            .externalToolPatterns(
                                                    context.harness().externalToolPatterns())
                                            .observability(harnessObservability)
                                            .compaction(context.harness().compaction())
                                            .toolResultEviction(context.harness().eviction())
                                            .build()
                                            .start(
                                                    request.input(),
                                                    RuntimeContext.builder()
                                                            .userId(request.userId())
                                                            .sessionId(request.threadId())
                                                            .metadata(request.metadata())
                                                            .build(),
                                                    request.streaming())));
                }));
    }

    private GatewayChatContext gatewayContext(ProtocolRunRequest request, AiAgent agent) {
        var requestId = "proto-" + UUID.randomUUID();
        return new GatewayChatContext(
                request.userId(),
                AGENT_SOURCE,
                request.threadId(),
                null,
                requestId,
                requestId,
                null,
                null,
                agent.getMetadata().getName(),
                null,
                null);
    }

    private HarnessToolkit toolkit(
            String userId, AiAgent agent, AgentContext.Context context) {
        return harnessToolkitFactory.create(
                context.tools(),
                (request, arguments) -> executeTool(userId, agent, request, arguments),
                context.harness().toolGroupsEnabled(),
                context.harness().asyncToolPatterns(),
                userId + ":protocol");
    }

    private HarnessToolkit.ToolResult executeTool(
            String userId, AiAgent agent, ToolExecutionRequest request, JsonNode arguments) {
        var call = new AiContentPart.ToolCall(
                request.id(),
                request.name(),
                arguments == null ? null : arguments,
                null);
        var outcome = chatTools.execute(userId, agent, call).block();
        if (outcome == null) {
            throw new IllegalStateException("工具执行返回空结果。");
        }
        return new HarnessToolkit.ToolResult(outcome.output(), outcome.isError());
    }

    private static String systemPrompt(
            ProtocolRunRequest request, AiAgent agent, AgentContext.Context context) {
        var prompt = StringUtils.hasText(request.systemPrompt())
                ? request.systemPrompt()
                : StringUtils.hasText(agent.getSpec().getPrompt())
                        ? agent.getSpec().getPrompt()
                        : ChatPrompts.defaultSystemPrompt();
        if (StringUtils.hasText(context.systemAdditions())) {
            return prompt + "\n\n" + context.systemAdditions();
        }
        return prompt;
    }

    public record ProtocolRunRequest(
            String userId,
            @Nullable String agentName,
            String threadId,
            @Nullable String systemPrompt,
            UserMessage input,
            boolean streaming,
            Map<String, Object> metadata) {

        public ProtocolRunRequest {
            if (!StringUtils.hasText(userId)) {
                throw new IllegalArgumentException("用户标识不能为空。");
            }
            if (!StringUtils.hasText(threadId)) {
                throw new IllegalArgumentException("线程标识不能为空。");
            }
            Objects.requireNonNull(input, "输入消息不能为空");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
