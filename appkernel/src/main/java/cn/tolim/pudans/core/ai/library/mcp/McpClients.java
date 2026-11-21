package cn.tolim.pudans.core.ai.library.mcp;

import cn.tolim.pudans.contract.ai.resource.AiMcpServer;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import dev.langchain4j.mcp.client.McpApplicationErrorException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 通过 LangChain4j MCP 客户端访问 Streamable HTTP 与 STDIO 服务器。
@Service
@RequiredArgsConstructor
public class McpClients implements DisposableBean {

    static final String CODE_INVALID = "ai-mcp-server-invalid";

    static final String CODE_REQUEST_FAILED = "ai-mcp-request-failed";

    static final String CODE_REQUEST_TIMEOUT = "ai-mcp-request-timeout";

    static final String CODE_STDIO_FAILED = "ai-mcp-stdio-failed";

    private static final int MAX_TOOLS = 2000;

    private final McpClientFactory clientFactory;

    private final Map<String, ClientHolder> clients = new ConcurrentHashMap<>();

    public record McpTool(String name, @Nullable String description, @Nullable JsonNode inputSchema) {}

    public record McpToolCallResult(String output, boolean isError) {}

    public Mono<List<McpTool>> listTools(AiMcpServer server) {
        return Mono.fromCallable(() -> client(server).listTools().stream()
                        .limit(MAX_TOOLS)
                        .map(McpClients::tool)
                        .toList())
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(this::mapError);
    }

    public Mono<McpToolCallResult> callTool(AiMcpServer server, String toolName, @Nullable JsonNode arguments) {
        return Mono.fromCallable(() -> call(
                        client(server),
                        ToolExecutionRequest.builder()
                                .name(toolName)
                                .arguments(arguments == null ? "{}" : arguments.toString())
                                .build()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(this::mapError);
    }

    public Mono<List<McpClient>> clients(List<AiMcpServer> servers) {
    return Mono.fromCallable(() -> servers.stream().map(this::client).toList())
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(this::mapError);
}

public Mono<List<McpResource>> listResources(AiMcpServer server) {
    return Mono.fromCallable(() -> client(server).listResources())
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(this::mapError);
}

public Mono<McpReadResourceResult> readResource(AiMcpServer server, String uri) {
    return Mono.fromCallable(() -> client(server).readResource(uri))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(this::mapError);
}

public Mono<List<McpPrompt>> listPrompts(AiMcpServer server) {
    return Mono.fromCallable(() -> client(server).listPrompts())
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(this::mapError);
}

public Mono<McpGetPromptResult> getPrompt(
            AiMcpServer server, String name, @Nullable Map<String, Object> arguments) {
    var values = arguments == null ? Map.<String, Object>of() : arguments;
    return Mono.fromCallable(() -> client(server).getPrompt(name, values))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(this::mapError);
}

public void close(String serverName) {
        var holder = clients.remove(serverName);
        if (holder != null) {
            holder.close();
        }
    }

    @Override
    public void destroy() {
        clients.values().forEach(ClientHolder::close);
        clients.clear();
    }

    private McpClient client(AiMcpServer server) {
        var name = server.getMetadata().getName();
        var fingerprint = fingerprint(server);
        return clients.compute(name, (key, existing) -> {
                    if (existing != null && existing.fingerprint().equals(fingerprint)) {
                        return existing;
                    }
                    var client = clientFactory.create(server);
                    if (existing != null) {
                        existing.close();
                    }
                    return new ClientHolder(fingerprint, client);
                })
                .client();
    }

    private static McpTool tool(ToolSpecification tool) {
        return new McpTool(tool.name(), tool.description(), schema(tool.parameters()));
    }

    private static @Nullable JsonNode schema(@Nullable JsonObjectSchema parameters) {
        if (parameters == null) {
            return null;
        }
        return JsonUtils.mapper().valueToTree(JsonSchemaElementJsonUtils.toMap(parameters));
    }

    private static McpToolCallResult call(McpClient client, ToolExecutionRequest request) {
        try {
            return result(client.executeTool(request));
        } catch (McpApplicationErrorException error) {
            return new McpToolCallResult(error.getMessage(), true);
        }
    }

    private static McpToolCallResult result(ToolExecutionResult result) {
        return new McpToolCallResult(result.resultText(), result.isError());
    }

    private static String fingerprint(AiMcpServer server) {
        var spec = server.getSpec();
        var builder = new StringBuilder();
        builder.append(spec.getTransport()).append('\u0000');
        builder.append(spec.getUrl()).append('\u0000');
        builder.append(spec.getCommand()).append('\u0000');
        if (spec.getArgs() != null) {
            spec.getArgs().forEach(argument -> builder.append(argument).append('\u0000'));
        }
        append(builder, spec.getEnv());
        append(builder, spec.getHeaders());
        return builder.toString();
    }

    private static void append(StringBuilder builder, @Nullable Map<String, String> values) {
        if (values == null) {
            return;
        }
        values.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> builder
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append('\u0000'));
    }

    private ApiException mapError(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException;
        }
        if (hasCause(error, TimeoutException.class)) {
            return timedOut("MCP 请求超时。");
        }
        var message = StringUtils.hasText(error.getMessage()) ? error.getMessage() : "MCP 请求失败。";
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_REQUEST_FAILED, message, error);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        var current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ApiException timedOut(String message) {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, CODE_REQUEST_TIMEOUT, message);
    }

    private record ClientHolder(String fingerprint, McpClient client) {

        private void close() {
            try {
                client.close();
            } catch (Exception ignored) {
                // 关闭失败不阻断服务器配置更新。
            }
        }
    }
}