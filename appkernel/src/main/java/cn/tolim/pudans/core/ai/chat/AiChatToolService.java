package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
import cn.tolim.pudans.core.ai.agent.GatewayChatModel;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.hook.HookService;
import cn.tolim.pudans.core.ai.library.mcp.McpServerService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.library.subagent.SubagentService;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimeService;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.service.AiServices;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 执行模型发起的工具调用，并把生命周期事件交给 Hook。
@Component
@RequiredArgsConstructor
public class AiChatToolService {

    private static final String TOOL_SKILL = "skill";

    private static final String TOOL_SUBAGENT = "subagent";

    private static final String TOOL_RUNTIME_EXEC = "runtime_exec";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration SUBAGENT_TIMEOUT = Duration.ofSeconds(180);

    private static final int MAX_OUTPUT_CHARS = 65536;

    private static final int SUBAGENT_MAX_OUTPUT_TOKENS = 2048;

    private final LibraryView libraryView;

    private final SkillService skillService;

    private final SubagentService subagentService;

    private final HookService hookService;

    private final AiGatewayService gatewayService;

    private final AgentRuntimeService runtimeService;

    public Mono<ToolOutcome> execute(String userId, AiAgent agent, AiContentPart.ToolCall call) {
        var agentName = agent.getMetadata().getName();
        var arguments = call.arguments() == null ? JsonUtils.mapper().createObjectNode() : call.arguments();
        var startedAt = System.nanoTime();
        var pre = hookService.dispatch(
                agentName,
                AiHook.Event.PRE_TOOL_USE,
                Map.of("tool", call.name(), "arguments", arguments.toString()));
        var execution = pre.then(executeCore(userId, agent, arguments, call.name()))
                .timeout(call.name().equals(TOOL_SUBAGENT) ? SUBAGENT_TIMEOUT : DEFAULT_TIMEOUT)
                .onErrorResume(error -> Mono.just(new RawOutcome(errorMessage(error), true)))
                .map(raw -> outcome(call, arguments, raw, startedAt));
        return execution.flatMap(outcome -> hookService
                .dispatch(
                        agentName,
                        AiHook.Event.POST_TOOL_USE,
                        Map.of(
                                "tool", call.name(),
                                "arguments", arguments.toString(),
                                "output", outcome.output(),
                                "isError", outcome.isError()))
                .thenReturn(outcome));
    }

    private Mono<RawOutcome> executeCore(
            String userId, AiAgent agent, JsonNode arguments, String toolName) {
        if (toolName.startsWith(McpServerService.TOOL_PREFIX)) {
            return libraryView
                    .callMcpTool(agent.getMetadata().getName(), toolName, arguments)
                    .map(result -> new RawOutcome(result.output(), result.isError()));
        }
        return switch (toolName) {
            case TOOL_SKILL -> skill(agent, arguments);
            case TOOL_SUBAGENT -> subagent(userId, agent, arguments);
            case TOOL_RUNTIME_EXEC -> runtimeExec(userId, agent, arguments);
            default -> Mono.just(new RawOutcome("未知工具：" + toolName, true));
        };
    }

    private Mono<RawOutcome> runtimeExec(String userId, AiAgent agent, JsonNode arguments) {
        var command = text(arguments, "command");
        if (!StringUtils.hasText(command)) {
            return Mono.just(new RawOutcome("缺少命令。", true));
        }
        var args = stringArray(arguments, "args");
        var workdir = text(arguments, "workdir");
        var timeout = arguments.has("timeoutSeconds") && arguments.get("timeoutSeconds").canConvertToInt()
                ? arguments.get("timeoutSeconds").asInt()
                : null;
        return runtimeService
                .exec(
                        agent.getMetadata().getName(),
                        userId,
                        command,
                        args,
                        StringUtils.hasText(workdir) ? workdir : null,
                        timeout)
                .map(result -> new RawOutcome(
                        "exitCode: " + result.exitCode() + "\nstdout:\n" + result.stdout()
                                + "\nstderr:\n" + result.stderr(),
                        result.exitCode() != 0));
    }

    private static List<String> stringArray(JsonNode node, String name) {
        var array = node == null ? null : node.get(name);
        if (array == null || !array.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        array.forEach(value -> {
            if (value != null && !value.isNull()) {
                values.add(value.asText());
            }
        });
        return List.copyOf(values);
    }

    private Mono<RawOutcome> skill(AiAgent agent, JsonNode arguments) {
        var name = text(arguments, "name");
        if (!StringUtils.hasText(name)) {
            return Mono.just(new RawOutcome("缺少技能名称。", true));
        }
        return skillService
                .byAgent(agent.getMetadata().getName())
                .filter(skill -> name.equals(skill.getMetadata().getName())
                        || (skill.getSpec() != null && name.equals(skill.getSpec().getDisplayName())))
                .next()
                .map(skill -> new RawOutcome(
                        skill.getSpec() == null ? "" : skill.getSpec().getContent(), false))
                .switchIfEmpty(Mono.just(new RawOutcome("技能不存在：" + name, true)));
    }

    private Mono<RawOutcome> subagent(String userId, AiAgent agent, JsonNode arguments) {
        var name = text(arguments, "name");
        var prompt = text(arguments, "prompt");
        if (!StringUtils.hasText(name) || !StringUtils.hasText(prompt)) {
            return Mono.just(new RawOutcome("缺少子代理名称或任务描述。", true));
        }
        return subagentService
                .resolve(agent.getMetadata().getName(), name)
                .flatMap(subagent -> runSubagent(userId, agent, subagent, prompt))
                .map(content -> new RawOutcome(content, false))
                .switchIfEmpty(Mono.just(new RawOutcome("子代理不存在：" + name, true)))
                .onErrorResume(error -> Mono.just(new RawOutcome(errorMessage(error), true)));
    }

    private Mono<String> runSubagent(
            String userId, AiAgent agent, AiSubagent subagent, String prompt) {
        var spec = subagent.getSpec();
        var systemPrompt = spec == null || !StringUtils.hasText(spec.getPrompt())
                ? ChatPrompts.SUBAGENT_DEFAULT
                : spec.getPrompt();
        var requestId = "sub-" + UUID.randomUUID();
        var context = new GatewayChatContext(
                userId,
                "pudans-subagent",
                "subagent-" + agent.getMetadata().getName(),
                null,
                requestId,
                requestId,
                null,
                spec == null ? null : spec.getRouteGroup(),
                agent.getMetadata().getName(),
                null,
                spec == null ? null : spec.getModel());
        return Mono.fromCallable(() -> {
                    var assistant = AiServices.builder(SubagentAssistant.class)
                            .chatModel(new GatewayChatModel(gatewayService, context))
                            .systemMessage(systemPrompt)
                            .chatRequestTransformer(request -> request.toBuilder()
                                    .temperature(0.2)
                                    .maxOutputTokens(SUBAGENT_MAX_OUTPUT_TOKENS)
                                    .build())
                            .build();
                    return assistant.chat(prompt);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static ToolOutcome outcome(
            AiContentPart.ToolCall call, JsonNode arguments, RawOutcome raw, long startedAt) {
        var output = raw.output() == null ? "" : raw.output();
        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(0, MAX_OUTPUT_CHARS) + "\n…（输出已截断）";
        }
        var durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return new ToolOutcome(
                call.id(),
                call.name(),
                serverOf(call.name()),
                arguments,
                output,
                raw.isError(),
                durationMs);
    }

    private static @Nullable String serverOf(String toolName) {
        if (!toolName.startsWith(McpServerService.TOOL_PREFIX)) {
            return null;
        }
        var rest = toolName.substring(McpServerService.TOOL_PREFIX.length());
        var separator = rest.indexOf('.');
        return separator > 0 ? rest.substring(0, separator) : null;
    }

    private static String text(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    interface SubagentAssistant {

        String chat(String prompt);
    }

    private static String errorMessage(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? "工具执行失败：" + message : "工具执行失败。";
    }

    public record ToolOutcome(
            String id,
            String name,
            @Nullable String server,
            JsonNode arguments,
            String output,
            boolean isError,
            long durationMs) {}

    private record RawOutcome(String output, boolean isError) {}
}