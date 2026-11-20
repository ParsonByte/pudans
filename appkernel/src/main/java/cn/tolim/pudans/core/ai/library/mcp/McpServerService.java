package cn.tolim.pudans.core.ai.library.mcp;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.core.extension.ai.AiMcpServer;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    public static final String TOOL_PREFIX = "mcp.";

    static final String CODE_TOOL_NOT_FOUND = "ai-mcp-tool-not-found";

    static final String CODE_TOOL_NOT_ALLOWED = "ai-mcp-tool-not-allowed";

    static final String CODE_SERVER_NOT_FOUND = "ai-mcp-server-not-found";

    private static final String RESOURCE_LIST = "mcp_list_resources";

    private static final String RESOURCE_GET = "mcp_get_resource";

    private static final String PROMPT_LIST = "mcp_prompt_list";

    private static final String PROMPT_GET = "mcp_prompt_get";

    private final ReactiveExtensionClient client;

    private final McpClientService mcpClient;

    public record McpToolDefinition(
            String name,
            String server,
            String tool,
            @Nullable String description,
            @Nullable JsonNode inputSchema) {}

    public record McpToolCatalog(List<McpToolDefinition> tools) {}

    public record McpServerView(
            String name,
            @Nullable String displayName,
            @Nullable String description,
            AiMcpServer.Transport transport,
            boolean enabled,
            AiMcpServer.Health health,
            int toolCount,
            List<McpClientService.McpTool> tools,
            @Nullable String message) {}

    public record TestResult(
            boolean healthy,
            int toolCount,
            List<McpClientService.McpTool> tools,
            @Nullable String message) {}

    public static Mono<Void> validate(AiMcpServer server) {
        return Mono.fromRunnable(() -> {
            var spec = server == null ? null : server.getSpec();
            if (spec == null) {
                throw invalid("MCP 服务器配置不能为空。");
            }
            if (!StringUtils.hasText(spec.getDisplayName())) {
                throw invalid("MCP 服务器 displayName 不能为空。");
            }
            var transport = spec.getTransport();
            if (transport == null) {
                throw invalid("MCP 服务器 transport 不能为空。");
            }
            switch (transport) {
                case STDIO -> {
                    if (!StringUtils.hasText(spec.getCommand())) {
                        throw invalid("STDIO 传输必须配置 command。");
                    }
                    if (StringUtils.hasText(spec.getUrl())) {
                        throw invalid("STDIO 传输不能配置 url。");
                    }
                    requireNoNullValues(spec.getArgs(), "args");
                }
                case HTTP, SSE -> {
                    if (!StringUtils.hasText(spec.getUrl())) {
                        throw invalid("HTTP/SSE 传输必须配置 url。");
                    }
                    requireHttpUrl(spec.getUrl());
                    if (StringUtils.hasText(spec.getCommand())) {
                        throw invalid("HTTP/SSE 传输不能配置 command。");
                    }
                    if (spec.getArgs() != null && !spec.getArgs().isEmpty()) {
                        throw invalid("HTTP/SSE 传输不能配置 args。");
                    }
                }
                case WEBSOCKET -> {
                    try {
                        McpUrls.requireWebSocket(spec.getUrl());
                    } catch (IllegalArgumentException error) {
                        throw invalid(error.getMessage());
                    }
                    if (StringUtils.hasText(spec.getCommand())) {
                        throw invalid("WebSocket 传输不能配置 command。");
                    }
                    if (spec.getArgs() != null && !spec.getArgs().isEmpty()) {
                        throw invalid("WebSocket 传输不能配置 args。");
                    }
                }
            }
            validateToolsJson(spec.getToolsJson());
            requireNoBlankEntries(spec.getToolAllowlist(), "toolAllowlist");
            requireNoBlankEntries(spec.getAgents(), "agents");
        });
    }

    public Flux<AiMcpServer> enabledForAgent(String agentName) {
        return client.listAll(AiMcpServer.class, ListOptions.builder().build(), defaultSort())
                .filter(server -> server.getSpec() != null && server.getSpec().isEnabled())
                .filter(server -> server.getSpec().getAgents() != null
                        && server.getSpec().getAgents().contains(agentName));
    }

    public Flux<McpServerView> byAgent(String agentName) {
        return enabledForAgent(agentName).flatMapSequential(this::view, 4);
    }

    public Mono<McpToolCatalog> toolCatalog(String agentName) {
        return catalog(enabledForAgent(agentName));
    }

    public Mono<McpToolCatalog> toolCatalogFor(List<String> serverNames) {
        return catalog(byNames(serverNames));
    }

    public Mono<McpClientService.McpToolCallResult> callTool(
            String agentName, String exposedName, @Nullable JsonNode arguments) {
        if (invalidToolName(exposedName)) {
            return Mono.error(toolNotFound(exposedName));
        }
        return callTool(resolveTool(enabledForAgent(agentName), exposedName), arguments);
    }

    public Mono<McpClientService.McpToolCallResult> callToolFor(
            List<String> serverNames, String exposedName, @Nullable JsonNode arguments) {
        if (invalidToolName(exposedName)) {
            return Mono.error(toolNotFound(exposedName));
        }
        return callTool(resolveTool(byNames(serverNames), exposedName), arguments);
    }

    private static boolean invalidToolName(@Nullable String exposedName) {
        return !StringUtils.hasText(exposedName) || !exposedName.startsWith(TOOL_PREFIX);
    }

    private Mono<McpClientService.McpToolCallResult> callTool(
            Mono<ResolvedTool> resolved, @Nullable JsonNode arguments) {
        return resolved.flatMap(tool ->
                mcpClient.callTool(tool.server(), tool.tool(), arguments));
    }

    private Flux<AiMcpServer> byNames(List<String> serverNames) {
        if (serverNames == null || serverNames.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(serverNames)
                .distinct()
                .concatMap(name -> client.get(AiMcpServer.class, name)
                        .filter(server -> server.getSpec() != null && server.getSpec().isEnabled())
                        .onErrorResume(error -> Mono.empty()));
    }

    private Mono<McpToolCatalog> catalog(Flux<AiMcpServer> servers) {
        return servers
                .flatMapSequential(
                        server -> {
                            var fallback = staticTools(server);
                            return mcpClient
                                    .listTools(server)
                                    .map(live -> mergedDefinitions(server, fallback, live))
                                    .onErrorResume(error -> Mono.just(
                                            mergedDefinitions(server, fallback, List.of())));
                        },
                        4)
                .flatMapIterable(definitions -> definitions)
                .collectList()
                .map(McpToolCatalog::new);
    }

    private static List<McpToolDefinition> mergedDefinitions(
            AiMcpServer server,
            List<McpClientService.McpTool> staticTools,
            List<McpClientService.McpTool> liveTools) {
        var merged = new LinkedHashMap<String, McpClientService.McpTool>();
        for (var tool : allowed(server.getSpec(), staticTools)) {
            merged.put(tool.name(), tool);
        }
        for (var tool : allowed(server.getSpec(), liveTools)) {
            merged.put(tool.name(), tool);
        }
        return definitions(server, List.copyOf(merged.values()));
    }

    private static List<McpClientService.McpTool> staticTools(AiMcpServer server) {
        var spec = server.getSpec();
        if (spec == null || !StringUtils.hasText(spec.getToolsJson())) {
            return List.of();
        }
        try {
            var root = JsonUtils.mapper().readTree(spec.getToolsJson());
            if (root == null || !root.isArray()) {
                return List.of();
            }
            var tools = new ArrayList<McpClientService.McpTool>();
            for (var item : root) {
                var name = item.path("name").asText("");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                var description = item.path("description").asText(null);
                var schema = item.get("inputSchema");
                tools.add(new McpClientService.McpTool(
                        name,
                        description,
                        schema == null || schema.isNull() ? null : schema));
            }
            return List.copyOf(tools);
        } catch (Exception error) {
            log.warn("MCP toolsJson 无法解析：{}。", server.getMetadata().getName(), error);
            return List.of();
        }
    }

    private static void validateToolsJson(@Nullable String toolsJson) {
        if (!StringUtils.hasText(toolsJson)) {
            return;
        }
        try {
            var root = JsonUtils.mapper().readTree(toolsJson);
            if (root == null || !root.isArray()) {
                throw invalid("MCP toolsJson 必须是 JSON 数组。");
            }
            for (var item : root) {
                if (!item.isObject() || !StringUtils.hasText(item.path("name").asText(""))) {
                    throw invalid("MCP toolsJson 的每个工具必须包含 name。");
                }
                var schema = item.get("inputSchema");
                if (schema != null && !schema.isNull() && !schema.isObject()) {
                    throw invalid("MCP toolsJson 的 inputSchema 必须是 JSON 对象。");
                }
            }
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("MCP toolsJson 无法解析。");
        }
    }

    public Mono<HarnessToolkit> decorateToolkit(HarnessToolkit toolkit, String agentName) {
        return enabledForAgent(agentName)
                .collectList()
                .flatMap(mcpClient::clients)
                .map(clients -> {
                    if (clients.isEmpty()) {
                        return toolkit;
                    }
                    var presenter = DefaultMcpResourcesAsToolsPresenter.builder()
                            .nameOfListResourcesTool(RESOURCE_LIST)
                            .nameOfGetResourceTool(RESOURCE_GET)
                            .build();
                    toolkit.register(
                            presenter.createListResourcesSpecification(),
                            presenter.createListResourcesExecutor(clients));
                    toolkit.register(
                            presenter.createGetResourceSpecification(),
                            presenter.createGetResourceExecutor(clients));
                    toolkit.register(
                            promptListSpecification(),
                            (request, memoryId) -> promptListJson(agentName, request.arguments()));
                    toolkit.register(
                            promptGetSpecification(),
                            (request, memoryId) -> promptGetJson(agentName, request.arguments()));
                    return toolkit;
                })
                .onErrorResume(error -> {
                    log.warn("MCP 资源工具注册失败：{}。", agentName, error);
                    return Mono.just(toolkit);
                });
    }

    private static ToolSpecification promptListSpecification() {
        return ToolSpecification.builder()
                .name(PROMPT_LIST)
                .description("列出指定 MCP 服务器的提示模板。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("server", "MCP 服务器名称")
                        .required("server")
                        .build())
                .build();
    }

    private static ToolSpecification promptGetSpecification() {
        return ToolSpecification.builder()
                .name(PROMPT_GET)
                .description("读取指定 MCP 服务器的提示模板。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("server", "MCP 服务器名称")
                        .addStringProperty("name", "提示模板名称")
                        .addStringProperty("arguments", "提示参数 JSON 对象字符串，可选")
                        .required("server", "name")
                        .build())
                .build();
    }

    private String promptListJson(String agentName, @Nullable String rawArguments) {
        var arguments = arguments(rawArguments);
        var serverName = arguments.path("server").asText("");
        var prompts = requireServer(agentName, serverName)
                .flatMap(mcpClient::listPrompts)
                .block();
        if (prompts == null) {
            throw new IllegalStateException("MCP 提示模板查询失败。");
        }
        return promptsJson(prompts);
    }

    private String promptGetJson(String agentName, @Nullable String rawArguments) {
        var arguments = arguments(rawArguments);
        var serverName = arguments.path("server").asText("");
        var name = arguments.path("name").asText("");
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name 不能为空。");
        }
        var result = requireServer(agentName, serverName)
                .flatMap(server -> mcpClient.getPrompt(server, name, promptArguments(arguments)))
                .block();
        if (result == null) {
            throw new IllegalStateException("MCP 提示模板读取失败。");
        }
        return promptJson(result);
    }

    private Mono<AiMcpServer> requireServer(String agentName, String serverName) {
        if (!StringUtils.hasText(serverName)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_SERVER_NOT_FOUND, "server 不能为空。"));
        }
        return enabledForAgent(agentName)
                .filter(server -> serverName.equals(server.getMetadata().getName()))
                .next()
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        CODE_SERVER_NOT_FOUND,
                        "MCP 服务器不存在：" + serverName + "。")));
    }

    private static JsonNode arguments(@Nullable String rawArguments) {
        if (!StringUtils.hasText(rawArguments)) {
            return JsonUtils.mapper().createObjectNode();
        }
        try {
            var node = JsonUtils.mapper().readTree(rawArguments);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("工具参数必须是 JSON 对象。");
            }
            return node;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("工具参数不是有效 JSON。", error);
        }
    }

    private static Map<String, Object> promptArguments(JsonNode arguments) {
        var value = arguments.get("arguments");
        if (value == null || value.isNull()) {
            return Map.of();
        }
        if (value.isTextual()) {
            var text = value.asText("");
            if (!StringUtils.hasText(text)) {
                return Map.of();
            }
            try {
                var parsed = JsonUtils.mapper().readTree(text);
                return parsed == null || !parsed.isObject()
                        ? Map.of()
                        : JsonUtils.mapper().convertValue(
                                parsed, new TypeReference<Map<String, Object>>() {});
            } catch (Exception error) {
                throw new IllegalArgumentException("arguments 不是有效 JSON 对象。", error);
            }
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("arguments 必须是 JSON 对象。");
        }
        return JsonUtils.mapper().convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private static String promptsJson(List<McpPrompt> prompts) {
        var array = JsonUtils.mapper().createArrayNode();
        for (var prompt : prompts) {
            var node = array.addObject();
            node.put("name", prompt.name());
            if (StringUtils.hasText(prompt.description())) {
                node.put("description", prompt.description());
            }
            var arguments = node.putArray("arguments");
            if (prompt.arguments() != null) {
                for (var argument : prompt.arguments()) {
                    var item = arguments.addObject();
                    item.put("name", argument.name());
                    if (StringUtils.hasText(argument.description())) {
                        item.put("description", argument.description());
                    }
                    item.put("required", argument.required());
                }
            }
        }
        return array.toString();
    }

    private static String promptJson(McpGetPromptResult result) {
        var node = JsonUtils.mapper().createObjectNode();
        if (StringUtils.hasText(result.description())) {
            node.put("description", result.description());
        }
        var messages = node.putArray("messages");
        for (var message : result.messages()) {
            var item = messages.addObject();
            item.put("role", message.role().name().toLowerCase(Locale.ROOT));
            item.put("content", String.valueOf(message.content()));
        }
        return node.toString();
    }

    public Mono<TestResult> test(String name) {
        return client.get(AiMcpServer.class, name).flatMap(server -> mcpClient
                .listTools(server)
                .map(tools -> {
                    var allowed = allowed(server.getSpec(), tools);
                    return new TestResult(true, allowed.size(), allowed, "连通性正常。");
                })
                .onErrorResume(error -> Mono.just(new TestResult(false, 0, List.of(), message(error))))
                .flatMap(result -> updateStatus(server, result)));
    }

    public Mono<AiMcpServer> setEnabled(String name, boolean enabled) {
        return client.get(AiMcpServer.class, name)
                .flatMap(server -> {
                    if (server.getSpec() == null) {
                        server.setSpec(new AiMcpServer.Spec());
                    }
                    server.getSpec().setEnabled(enabled);
                    return client.update(server);
                })
                .doOnNext(server -> {
                    if (!enabled) {
                        mcpClient.close(name);
                    }
                });
    }

    private Mono<McpServerView> view(AiMcpServer server) {
        var spec = server.getSpec();
        var name = server.getMetadata().getName();
        return mcpClient
                .listTools(server)
                .map(tools -> allowed(spec, tools))
                .map(tools -> new McpServerView(
                        name,
                        spec.getDisplayName(),
                        spec.getDescription(),
                        spec.getTransport(),
                        spec.isEnabled(),
                        AiMcpServer.Health.HEALTHY,
                        tools.size(),
                        tools,
                        null))
                .onErrorResume(error -> Mono.just(new McpServerView(
                        name,
                        spec.getDisplayName(),
                        spec.getDescription(),
                        spec.getTransport(),
                        spec.isEnabled(),
                        AiMcpServer.Health.UNHEALTHY,
                        0,
                        List.of(),
                        message(error))));
    }

    private Mono<ResolvedTool> resolveTool(Flux<AiMcpServer> servers, String exposedName) {
        if (invalidToolName(exposedName)) {
            return Mono.error(toolNotFound(exposedName));
        }
        return servers
                .collectList()
                .flatMap(available -> {
                    ResolvedTool matched = null;
                    for (var server : available) {
                        var serverName = server.getMetadata().getName();
                        var prefix = TOOL_PREFIX + serverName + ".";
                        if (exposedName.startsWith(prefix)
                                && (matched == null
                                        || serverName.length()
                                                > matched.server().getMetadata().getName().length())) {
                            matched = new ResolvedTool(server, exposedName.substring(prefix.length()));
                        }
                    }
                    if (matched == null || !StringUtils.hasText(matched.tool())) {
                        return Mono.error(toolNotFound(exposedName));
                    }
                    var allowlist = matched.server().getSpec().getToolAllowlist();
                    if (allowlist != null && !allowlist.isEmpty() && !allowlist.contains(matched.tool())) {
                        return Mono.error(toolNotAllowed(exposedName));
                    }
                    return Mono.just(matched);
                });
    }

    private static List<McpClientService.McpTool> allowed(
            AiMcpServer.Spec spec, List<McpClientService.McpTool> tools) {
        var allowlist = spec.getToolAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            return tools;
        }
        return tools.stream().filter(tool -> allowlist.contains(tool.name())).toList();
    }

    private static List<McpToolDefinition> definitions(
            AiMcpServer server, List<McpClientService.McpTool> tools) {
        var serverName = server.getMetadata().getName();
        return tools.stream()
                .map(tool -> new McpToolDefinition(
                        TOOL_PREFIX + serverName + "." + tool.name(),
                        serverName,
                        tool.name(),
                        tool.description(),
                        tool.inputSchema()))
                .toList();
    }

    private Mono<TestResult> updateStatus(AiMcpServer server, TestResult result) {
        var status = server.getStatus() == null ? new AiMcpServer.Status() : server.getStatus();
        status.setHealth(result.healthy() ? AiMcpServer.Health.HEALTHY : AiMcpServer.Health.UNHEALTHY);
        status.setToolCount(result.toolCount());
        status.setLastCheckedAt(Instant.now());
        status.setMessage(result.message());
        server.setStatus(status);
        return client.update(server).thenReturn(result);
    }

    private static String message(Throwable error) {
        if (error instanceof ApiException apiException && apiException.getReason() != null) {
            return apiException.getReason();
        }
        return "MCP 服务器探测失败。";
    }

    private static void requireHttpUrl(String url) {
        try {
            AiProviderHttpSupport.requireHttpUrl(url, "MCP 服务器 url");
        } catch (IllegalArgumentException error) {
            throw invalid(error.getMessage());
        }
    }

    private static void requireNoNullValues(@Nullable List<String> values, String label) {
        if (values != null && values.stream().anyMatch(Objects::isNull)) {
            throw invalid(label + " 不能包含空值。");
        }
    }

    private static void requireNoBlankEntries(@Nullable List<String> values, String label) {
        if (values == null) {
            return;
        }
        if (values.stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw invalid(label + " 不能包含空值。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, McpClientService.CODE_INVALID, message);
    }

    private static ApiException toolNotFound(@Nullable String exposedName) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                CODE_TOOL_NOT_FOUND,
                "MCP 工具不存在：" + Objects.toString(exposedName, "") + "。");
    }

    private static ApiException toolNotAllowed(String exposedName) {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                CODE_TOOL_NOT_ALLOWED,
                "MCP 工具未在允许清单中：" + exposedName + "。");
    }

    private record ResolvedTool(AiMcpServer server, String tool) {}
}