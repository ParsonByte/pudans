package cn.tolim.pudans.core.ai.library.mcp;

import cn.tolim.pudans.contract.ai.AiMcpServer;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.PudansProperties;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class McpClientFactory {

    static final String CLIENT_NAME = "pudans";

    static final String CLIENT_VERSION = "v1alpha1";

    private static final Duration INITIALIZE_TIMEOUT = Duration.ofSeconds(20);

    private static final Duration TOOL_CALL_TIMEOUT = Duration.ofSeconds(60);

    private final Path stdioRoot;

    public McpClientFactory(PudansProperties properties) {
        this.stdioRoot = Objects.requireNonNull(properties, "Pudans 配置不能为空")
                .getWorkDir()
                .toAbsolutePath()
                .normalize()
                .resolve("mcp");
    }

    public McpClient create(AiMcpServer server) {
        var transport = transport(server);
        return new DefaultMcpClient.Builder()
                .transport(transport)
                .clientName(CLIENT_NAME)
                .clientVersion(CLIENT_VERSION)
                .initializationTimeout(INITIALIZE_TIMEOUT)
                .toolExecutionTimeout(TOOL_CALL_TIMEOUT)
                .toolResultConverter(McpClientFactory::result)
                .build();
    }

    private McpTransport transport(AiMcpServer server) {
        var spec = spec(server);
        return switch (spec.getTransport()) {
            case STDIO -> stdio(server.getMetadata().getName(), spec);
            case HTTP, SSE -> http(spec);
            case WEBSOCKET -> websocket(spec);
        };
    }

    private McpTransport stdio(String serverName, AiMcpServer.Spec spec) {
        var command = command(spec);
        var builder = new StdioMcpTransport.Builder()
                .command(command)
                .workingDirectory(workDirectory(serverName));
        if (spec.getEnv() != null) {
            builder.environment(spec.getEnv().entrySet().stream()
                    .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        return builder.build();
    }

    private McpTransport http(AiMcpServer.Spec spec) {
        var url = requireUrl(spec);
        var builder = new StreamableHttpMcpTransport.Builder()
                .url(url)
                .timeout(TOOL_CALL_TIMEOUT);
        if (spec.getHeaders() != null) {
            builder.customHeaders(headers(spec));
        }
        return builder.build();
    }

    private McpTransport websocket(AiMcpServer.Spec spec) {
        String url;
        try {
            url = McpUrls.requireWebSocket(spec.getUrl());
        } catch (IllegalArgumentException error) {
            throw invalid(error.getMessage());
        }
        return new WebSocketMcpTransport.Builder()
                .url(url)
                .timeout(TOOL_CALL_TIMEOUT)
                .headersSupplier(() -> headers(spec))
                .build();
    }

    private static Map<String, String> headers(AiMcpServer.Spec spec) {
        if (spec.getHeaders() == null) {
            return Map.of();
        }
        return spec.getHeaders().entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static ToolExecutionResult result(List<Map<String, Object>> content, boolean isError) {
        var textOnly = content.stream().allMatch(item -> "text".equals(item.get("type")));
        var output = textOnly
                ? content.stream()
                        .map(item -> item.get("text") == null ? "" : String.valueOf(item.get("text")))
                        .collect(Collectors.joining("\n"))
                : JsonUtils.objectToJson(content);
        return ToolExecutionResult.builder()
                .isError(isError)
                .resultText(output)
                .build();
    }

    private static AiMcpServer.Spec spec(AiMcpServer server) {
        var spec = server == null ? null : server.getSpec();
        if (spec == null || spec.getTransport() == null) {
            throw invalid("MCP 服务器 transport 不能为空。");
        }
        return spec;
    }

    private static List<String> command(AiMcpServer.Spec spec) {
        var command = spec.getCommand();
        if (!StringUtils.hasText(command)) {
            throw invalid("STDIO 传输必须配置 command。");
        }
        var values = new ArrayList<String>();
        values.add(command.strip());
        if (spec.getArgs() != null) {
            for (var argument : spec.getArgs()) {
                if (argument == null) {
                    throw invalid("STDIO 传输的 args 不能包含空值。");
                }
                values.add(argument);
            }
        }
        return values;
    }

    private static String requireUrl(AiMcpServer.Spec spec) {
        try {
            return AiProviderHttpSupport.requireHttpUrl(spec.getUrl(), "MCP 服务器 url");
        } catch (IllegalArgumentException error) {
            throw invalid(error.getMessage());
        }
    }

    private Path workDirectory(String serverName) {
        var safeName = serverName.replaceAll("[^A-Za-z0-9._-]", "_");
        var directory = stdioRoot.resolve(safeName);
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    McpClients.CODE_STDIO_FAILED,
                    "无法创建 MCP STDIO 工作目录。",
                    error);
        }
        return directory;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, McpClients.CODE_INVALID, message);
    }
}