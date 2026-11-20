package cn.tolim.pudans.core.ai.library.mcp;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.core.extension.ai.AiMcpServer;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class McpServerService {

    public static final String TOOL_PREFIX = "mcp.";

    static final String CODE_TOOL_NOT_FOUND = "ai-mcp-tool-not-found";

    static final String CODE_TOOL_NOT_ALLOWED = "ai-mcp-tool-not-allowed";

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
            }
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
        return enabledForAgent(agentName)
                .flatMapSequential(
                        server -> mcpClient
                                .listTools(server)
                                .map(tools -> definitions(server, allowed(server.getSpec(), tools)))
                                .onErrorResume(error -> Mono.empty()),
                        4)
                .flatMapIterable(definitions -> definitions)
                .collectList()
                .map(McpToolCatalog::new);
    }

    public Mono<McpClientService.McpToolCallResult> callTool(
            String agentName, String exposedName, @Nullable JsonNode arguments) {
        return resolveTool(agentName, exposedName)
                .flatMap(resolved ->
                        mcpClient.callTool(resolved.server(), resolved.tool(), arguments));
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

    private Mono<ResolvedTool> resolveTool(String agentName, String exposedName) {
        if (!StringUtils.hasText(exposedName) || !exposedName.startsWith(TOOL_PREFIX)) {
            return Mono.error(toolNotFound(exposedName));
        }
        return enabledForAgent(agentName)
                .collectList()
                .flatMap(servers -> {
                    ResolvedTool matched = null;
                    for (var server : servers) {
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