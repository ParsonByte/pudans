package cn.tolim.pudans.core.ai.chat;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiHook;
import cn.tolim.pudans.contract.ai.resource.AiSubagent;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
import cn.tolim.pudans.core.ai.agent.GatewayChatModel;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.agent.HarnessAgent;
import cn.tolim.pudans.core.ai.harness.agent.InMemoryAgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceManager;
import cn.tolim.pudans.core.ai.harness.workspace.Workspaces;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceTools;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.hook.Hooks;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.library.skill.Skills;
import cn.tolim.pudans.core.ai.library.subagent.Subagents;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryHashes;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryTools;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.endpoint.MemoryViews;
import cn.tolim.pudans.core.ai.prompt.ChatPrompts;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimes;
import cn.tolim.pudans.core.ai.runtime.RuntimeTools;
import com.fasterxml.jackson.databind.JsonNode;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 执行模型发起的工具调用，并把生命周期事件交给 Hook。
@Component
@RequiredArgsConstructor
public class ChatTools {

    private static final String TOOL_SKILL = "skill";

    private static final String TOOL_SUBAGENT = "subagent";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration SUBAGENT_TIMEOUT = Duration.ofSeconds(180);

    private static final int MAX_OUTPUT_CHARS = 65536;

    private static final int SUBAGENT_MAX_OUTPUT_TOKENS = 2048;

    private final LibraryView libraryView;

    private final Skills skills;

    private final Subagents subagents;

    private final Hooks hooks;

    private final AiGateway gatewayService;

    private final AgentRuntimes runtimeService;

    private final Workspaces workspaces;

    private final MemoryViews memoryViewService;

    private final AiMemoryL1Repository memoryL1Repository;

    public Mono<ToolOutcome> execute(String userId, AiAgent agent, AiContentPart.ToolCall call) {
        var agentName = agent.getMetadata().getName();
        var arguments = call.arguments() == null ? JsonUtils.mapper().createObjectNode() : call.arguments();
        var startedAt = System.nanoTime();
        var pre = hooks.dispatch(
                agentName,
                AiHook.Event.PRE_TOOL_USE,
                Map.of("tool", call.name(), "arguments", arguments.toString()));
        var execution = pre.then(executeCore(userId, agent, arguments, call.name()))
                .timeout(call.name().equals(TOOL_SUBAGENT) ? SUBAGENT_TIMEOUT : DEFAULT_TIMEOUT)
                .onErrorResume(error -> Mono.just(new RawOutcome(errorMessage(error), true)))
                .map(raw -> outcome(call, arguments, raw, startedAt));
        return execution.flatMap(outcome -> hooks
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
        if (toolName.startsWith(McpServers.TOOL_PREFIX)) {
            return libraryView
                    .callMcpTool(agent.getMetadata().getName(), toolName, arguments)
                    .map(result -> new RawOutcome(result.output(), result.isError()));
        }
        return switch (toolName) {
            case TOOL_SKILL -> skill(agent, arguments);
            case TOOL_SUBAGENT -> subagent(userId, agent, arguments);
            case RuntimeTools.EXEC -> runtimeExec(userId, agent, arguments);
            case RuntimeTools.READ -> runtimeRead(userId, agent, arguments);
            case RuntimeTools.WRITE -> runtimeWrite(userId, agent, arguments);
            case RuntimeTools.LOGS -> runtimeLogs(userId, agent, arguments);
            case RuntimeTools.STATUS -> runtimeStatus(userId, agent);
            case RuntimeTools.START -> runtimeStart(userId, agent);
            case RuntimeTools.STOP -> runtimeStop(userId, agent);
            case RuntimeTools.SNAPSHOT -> runtimeSnapshot(userId, agent, arguments);
            case RuntimeTools.RESTORE -> runtimeRestore(userId, agent, arguments);
            case RuntimeTools.SNAPSHOTS -> runtimeSnapshots(userId, agent);
            case WorkspaceTools.LIST -> workspaceList(userId, agent, arguments);
            case WorkspaceTools.READ -> workspaceRead(userId, agent, arguments);
            case WorkspaceTools.WRITE -> workspaceWrite(userId, agent, arguments);
            case WorkspaceTools.DELETE -> workspaceDelete(userId, agent, arguments);
            case WorkspaceTools.MOVE -> workspaceMove(userId, agent, arguments);
            case WorkspaceTools.SNAPSHOT -> workspaceSnapshot(userId, agent, arguments);
            case MemoryTools.SEARCH -> memorySearch(userId, agent, arguments);
            case MemoryTools.ADD -> memoryAdd(userId, agent, arguments);
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

    private Mono<RawOutcome> runtimeRead(String userId, AiAgent agent, JsonNode arguments) {
        var path = text(arguments, "path");
        if (!StringUtils.hasText(path)) {
            return Mono.just(new RawOutcome("缺少 path。", true));
        }
        return runtimeService
                .readFile(agent.getMetadata().getName(), userId, path)
                .map(file -> new RawOutcome(
                        "encoding: " + file.encoding() + "\ncontent:\n" + file.content(), false));
    }

    private Mono<RawOutcome> runtimeWrite(String userId, AiAgent agent, JsonNode arguments) {
        var path = text(arguments, "path");
        if (!StringUtils.hasText(path)) {
            return Mono.just(new RawOutcome("缺少 path。", true));
        }
        var encoding = text(arguments, "encoding");
        return runtimeService
                .writeFile(
                        agent.getMetadata().getName(),
                        userId,
                        path,
                        StringUtils.hasText(encoding) ? encoding : "utf-8",
                        text(arguments, "content"))
                .thenReturn(new RawOutcome("写入成功。", false));
    }

    private Mono<RawOutcome> runtimeLogs(String userId, AiAgent agent, JsonNode arguments) {
        var limit = Math.clamp(intValue(arguments, "limit", 100), 1, 500);
        return runtimeService
                .logs(agent.getMetadata().getName(), userId, false, null)
                .take(limit)
                .map(event -> event.timestamp() + " [" + event.stream() + "] " + event.line())
                .collectList()
                .map(lines -> new RawOutcome(
                        lines.isEmpty() ? "无日志。" : String.join("\n", lines), false));
    }

    private Mono<RawOutcome> runtimeStatus(String userId, AiAgent agent) {
        return runtimeService
                .status(agent.getMetadata().getName(), userId)
                .map(status -> new RawOutcome(
                        "state: " + status.state()
                                + (StringUtils.hasText(status.message())
                                        ? "\nmessage: " + status.message()
                                        : ""),
                        false));
    }

    private Mono<RawOutcome> runtimeStart(String userId, AiAgent agent) {
        return runtimeService
                .start(agent.getMetadata().getName(), userId)
                .map(view -> new RawOutcome("state: " + view.state(), false));
    }

    private Mono<RawOutcome> runtimeStop(String userId, AiAgent agent) {
        return runtimeService
                .stop(agent.getMetadata().getName(), userId)
                .map(view -> new RawOutcome("state: " + view.state(), false));
    }

    private Mono<RawOutcome> runtimeSnapshot(
            String userId, AiAgent agent, JsonNode arguments) {
        var name = text(arguments, "name");
        return runtimeService
                .snapshot(
                        agent.getMetadata().getName(),
                        userId,
                        StringUtils.hasText(name) ? name : null,
                        userId)
                .map(view -> new RawOutcome(
                        "snapshotId: " + view.id() + "\nname: " + view.name()
                                + "\nsizeBytes: " + view.sizeBytes(),
                        false));
    }

    private Mono<RawOutcome> runtimeRestore(
            String userId, AiAgent agent, JsonNode arguments) {
        var value = arguments == null ? null : arguments.get("snapshotId");
        if (value == null || !value.canConvertToLong()) {
            return Mono.just(new RawOutcome("缺少 snapshotId。", true));
        }
        return runtimeService
                .restoreSnapshot(agent.getMetadata().getName(), userId, value.asLong())
                .thenReturn(new RawOutcome("快照已恢复。", false));
    }

    private Mono<RawOutcome> runtimeSnapshots(String userId, AiAgent agent) {
        return runtimeService
                .snapshots(agent.getMetadata().getName(), userId, 20)
                .map(view -> "id=" + view.id() + " name=" + view.name() + " size=" + view.sizeBytes())
                .collectList()
                .map(lines -> new RawOutcome(String.join("\n", lines), false));
    }

    private Mono<RawOutcome> workspaceList(String userId, AiAgent agent, JsonNode arguments) {
        return workspace(userId, agent, manager -> {
            var path = text(arguments, "path");
            var entries = manager.list(StringUtils.hasText(path) ? path : ".");
            var array = JsonUtils.mapper().createArrayNode();
            for (var entry : entries) {
                var node = array.addObject();
                node.put("name", entry.name());
                node.put("directory", entry.directory());
                node.put("size", entry.size());
            }
            return new RawOutcome(array.toString(), false);
        });
    }

    private Mono<RawOutcome> workspaceRead(String userId, AiAgent agent, JsonNode arguments) {
        var path = text(arguments, "path");
        if (!StringUtils.hasText(path)) {
            return Mono.just(new RawOutcome("缺少 path。", true));
        }
        return workspace(userId, agent, manager -> new RawOutcome(
                manager.readText(path)
                        .orElseThrow(() -> new IllegalArgumentException("工作区文件不存在：" + path)),
                false));
    }

    private Mono<RawOutcome> workspaceWrite(String userId, AiAgent agent, JsonNode arguments) {
        var path = text(arguments, "path");
        if (!StringUtils.hasText(path)) {
            return Mono.just(new RawOutcome("缺少 path。", true));
        }
        return workspace(userId, agent, manager -> {
            manager.writeText(path, text(arguments, "content"));
            return new RawOutcome("写入成功。", false);
        });
    }

    private Mono<RawOutcome> workspaceDelete(String userId, AiAgent agent, JsonNode arguments) {
        var path = text(arguments, "path");
        if (!StringUtils.hasText(path)) {
            return Mono.just(new RawOutcome("缺少 path。", true));
        }
        return workspace(userId, agent, manager -> {
            manager.delete(path);
            return new RawOutcome("已删除：" + path, false);
        });
    }

    private Mono<RawOutcome> workspaceMove(String userId, AiAgent agent, JsonNode arguments) {
        var from = text(arguments, "from");
        var to = text(arguments, "to");
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
            return Mono.just(new RawOutcome("缺少 from 或 to。", true));
        }
        return workspace(userId, agent, manager -> {
            manager.move(from, to);
            return new RawOutcome("已移动：" + from + " -> " + to, false);
        });
    }

    private Mono<RawOutcome> workspaceSnapshot(String userId, AiAgent agent, JsonNode arguments) {
        var action = text(arguments, "action");
        return workspace(userId, agent, manager -> {
            if (!StringUtils.hasText(action) || "create".equalsIgnoreCase(action)) {
                return new RawOutcome(manager.snapshot(), false);
            }
            if ("list".equalsIgnoreCase(action)) {
                return new RawOutcome(
                        JsonUtils.objectToJson(manager.snapshots()), false);
            }
            if ("restore".equalsIgnoreCase(action)) {
                var id = text(arguments, "id");
                if (!StringUtils.hasText(id)) {
                    throw new IllegalArgumentException("恢复快照时 id 不能为空。");
                }
                manager.restore(id);
                return new RawOutcome("已恢复快照：" + id, false);
            }
            throw new IllegalArgumentException("未知快照操作：" + action);
        });
    }

    private Mono<RawOutcome> memorySearch(String userId, AiAgent agent, JsonNode arguments) {
        var query = text(arguments, "query");
        if (!StringUtils.hasText(query)) {
            return Mono.just(new RawOutcome("缺少 query。", true));
        }
        var team = teamOf(agent);
        if (team == null) {
            return Mono.just(new RawOutcome("Agent 未绑定团队，无法检索记忆。", true));
        }
        var limit = Math.clamp(intValue(arguments, "limit", 5), 1, 20);
        return memoryViewService
                .search(userId, team, agent.getMetadata().getName(), query, limit)
                .map(refs -> new RawOutcome(JsonUtils.objectToJson(refs), false));
    }

    private Mono<RawOutcome> memoryAdd(String userId, AiAgent agent, JsonNode arguments) {
        var content = text(arguments, "content");
        if (!StringUtils.hasText(content)) {
            return Mono.just(new RawOutcome("缺少 content。", true));
        }
        var team = teamOf(agent);
        if (team == null) {
            return Mono.just(new RawOutcome("Agent 未绑定团队，无法写入记忆。", true));
        }
        var record = L1MemoryRecord.newRecord(
                UUID.randomUUID().toString(),
                new MemoryContext(team, userId, agent.getMetadata().getName(), "chat-tool", null),
                null,
                content.strip(),
                MemoryType.WORK_FACT,
                50,
                "手动记忆",
                List.of(),
                Map.of("source", MemoryTools.ADD),
                MemoryHashes.sha256Hex(content.strip()),
                MemoryScope.PRIVATE,
                false,
                1,
                List.of(Instant.now()));
        return memoryL1Repository
                .insert(record)
                .map(saved -> new RawOutcome(
                        JsonUtils.objectToJson(Map.of(
                                "id", saved.id() == null ? 0 : saved.id(),
                                "recordId", saved.recordId())),
                        false));
    }

    private static String teamOf(AiAgent agent) {
        var spec = agent == null ? null : agent.getSpec();
        return spec == null || !StringUtils.hasText(spec.getTeam()) ? null : spec.getTeam();
    }

    private Mono<RawOutcome> workspace(
            String userId, AiAgent agent, Function<WorkspaceManager, RawOutcome> call) {
        return Mono.fromCallable(() -> call.apply(
                        workspaces.manager(agent.getMetadata().getName(), userId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static int intValue(JsonNode node, String name, int fallback) {
        var value = node == null ? null : node.get(name);
        return value == null || !value.canConvertToInt() ? fallback : value.asInt();
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
        return skills
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
        return subagents
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
        return HarnessAgent.builder()
                .name("subagent-" + agent.getMetadata().getName())
                .systemPrompt(systemPrompt)
                .chatModel(new GatewayChatModel(gatewayService, context))
                .stateStore(new InMemoryAgentStateStore())
                .temperature(0.2)
                .maxOutputTokens(SUBAGENT_MAX_OUTPUT_TOKENS)
                .build()
                .call(prompt, RuntimeContext.of(userId, "subagent-" + requestId))
                .map(result -> result.content());
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
        if (!toolName.startsWith(McpServers.TOOL_PREFIX)) {
            return null;
        }
        var rest = toolName.substring(McpServers.TOOL_PREFIX.length());
        var separator = rest.indexOf('.');
        return separator > 0 ? rest.substring(0, separator) : null;
    }

    private static String text(JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? "" : value.asText("");
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