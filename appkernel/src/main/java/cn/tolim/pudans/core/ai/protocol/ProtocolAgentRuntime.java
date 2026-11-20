package cn.tolim.pudans.core.ai.protocol;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
import cn.tolim.pudans.core.ai.agent.GatewayChatModel;
import cn.tolim.pudans.core.ai.agent.GatewayRunState;
import cn.tolim.pudans.core.ai.agent.GatewayStreamingChatModel;
import cn.tolim.pudans.core.ai.chat.AiAgentContextService;
import cn.tolim.pudans.core.ai.chat.AiChatToolService;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.HarnessAgent;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import cn.tolim.pudans.core.ai.team.AiTeamService;
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

    private final AiTeamService teamService;

    private final AiAgentContextService agentContextService;

    private final AiChatToolService toolService;

    private final AiGatewayService gatewayService;

    private final AgentStateStore stateStore;

    private final ThreadJournalStore journalStore;

    public Mono<AgentRun> start(ProtocolRunRequest request) {
        var agentName = StringUtils.hasText(request.agentName())
                ? request.agentName()
                : AiTeamService.DEFAULT_AGENT;
        return teamService
                .requireAgent(agentName, request.userId())
                .flatMap(agent -> agentContextService.build(agent).map(context -> {
                    var state = new GatewayRunState();
                    var gatewayContext = gatewayContext(request, agent);
                    var toolkit = toolkit(request.userId(), agent, context);
                    var run = HarnessAgent.builder()
                            .name(agent.getMetadata().getName())
                            .systemPrompt(systemPrompt(request, agent, context))
                            .chatModel(new GatewayChatModel(
                                    gatewayService, gatewayContext, state))
                            .streamingChatModel(new GatewayStreamingChatModel(
                                    gatewayService, gatewayContext, state))
                            .toolkit(toolkit)
                            .stateStore(stateStore)
                            .journalStore(journalStore)
                            .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                            .build()
                            .start(
                                    request.input(),
                                    RuntimeContext.builder()
                                            .userId(request.userId())
                                            .sessionId(request.threadId())
                                            .metadata(request.metadata())
                                            .build(),
                                    request.streaming());
                    return run;
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
            String userId, AiAgent agent, AiAgentContextService.AgentContext context) {
        var toolkit = new HarnessToolkit();
        for (var tool : context.tools()) {
            toolkit.register(tool, (request, arguments) ->
                    executeTool(userId, agent, request, arguments));
        }
        toolkit.fallbackHandler((request, arguments) -> executeTool(userId, agent, request, arguments));
        return toolkit;
    }

    private HarnessToolkit.ToolResult executeTool(
            String userId, AiAgent agent, ToolExecutionRequest request, JsonNode arguments) {
        var call = new AiContentPart.ToolCall(
                request.id(),
                request.name(),
                arguments == null ? null : arguments,
                null);
        var outcome = toolService.execute(userId, agent, call).block();
        if (outcome == null) {
            throw new IllegalStateException("工具执行返回空结果。");
        }
        return new HarnessToolkit.ToolResult(outcome.output(), outcome.isError());
    }

    private static String systemPrompt(
            ProtocolRunRequest request, AiAgent agent, AiAgentContextService.AgentContext context) {
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