package cn.tolim.pudans.core.ai.harness.tool;

import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jToolMapper;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HarnessToolkit {

    private final Map<ToolSpecification, ToolExecutor> executors = new LinkedHashMap<>();

    private final Map<String, ToolExecutor> namedExecutors = new LinkedHashMap<>();

    private final Set<String> names = new HashSet<>();

    private final ToolGroupManager groupManager = new ToolGroupManager();

    private final ToolSearchStrategy searchStrategy;

    private AsyncToolRegistry asyncRegistry;

    private ToolHandler fallbackHandler;

    public HarnessToolkit() {
        this(new KeywordToolSearchStrategy());
    }

    public HarnessToolkit(ToolSearchStrategy searchStrategy) {
        this.searchStrategy = Objects.requireNonNull(searchStrategy, "工具检索策略不能为空");
    }

    public HarnessToolkit register(Object bean) {
        return register(bean, null);
    }

    public HarnessToolkit register(Object bean, String groupName) {
        Objects.requireNonNull(bean, "工具对象不能为空");
        if (groupName != null) {
            groupManager.requireGroup(groupName);
        }
        for (var type = bean.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (var method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                put(specification(method), new DefaultToolExecutor(bean, method), groupName);
            }
        }
        return this;
    }

    public HarnessToolkit register(AiTool tool, ToolHandler handler) {
        return register(tool, handler, null, false);
    }

    public HarnessToolkit register(AiTool tool, ToolHandler handler, String groupName) {
        return register(tool, handler, groupName, false);
    }

    public HarnessToolkit registerAsync(AiTool tool, ToolHandler handler) {
        return register(tool, handler, null, true);
    }

    public HarnessToolkit registerAsync(AiTool tool, ToolHandler handler, String groupName) {
        return register(tool, handler, groupName, true);
    }

    private HarnessToolkit register(
            AiTool tool, ToolHandler handler, String groupName, boolean async) {
        Objects.requireNonNull(tool, "工具定义不能为空");
        Objects.requireNonNull(handler, "工具处理器不能为空");
        if (groupName != null) {
            groupManager.requireGroup(groupName);
        }
        var specification = LangChain4jToolMapper.toSpecifications(List.of(tool)).getFirst();
        var executor = async
                ? asyncExecutor(specification.name(), handler)
                : dynamicExecutor(handler);
        put(specification, executor, groupName);
        return this;
    }

    public ToolGroupManager groupManager() {
        return groupManager;
    }

    public HarnessToolkit asyncRegistry(AsyncToolRegistry asyncRegistry) {
        this.asyncRegistry = Objects.requireNonNull(asyncRegistry, "异步工具注册表不能为空");
        return this;
    }

    public HarnessToolkit fallbackHandler(ToolHandler fallbackHandler) {
        this.fallbackHandler = Objects.requireNonNull(fallbackHandler, "工具回退处理器不能为空");
        return this;
    }

    public Map<ToolSpecification, ToolExecutor> executors() {
        return Collections.unmodifiableMap(executors);
    }

    public List<ToolSpecification> specifications() {
        return List.copyOf(executors.keySet());
    }

    public List<ToolSpecification> activeSpecifications() {
        return executors.keySet().stream()
                .filter(specification -> groupManager.isActiveTool(specification.name()))
                .toList();
    }

    public ToolSearchResult search(String query, int limit) {
        return searchStrategy.search(new ToolSearchRequest(activeSpecifications(), query, limit));
    }

    public ToolResult execute(ToolExecutionRequest request) {
        var executor = namedExecutors.get(request.name());
        if (executor == null) {
            // 模型可能调用未声明的工具，交由回退处理器按用户权限执行。
            if (fallbackHandler != null) {
                try {
                    return fallbackHandler.execute(
                            request, LangChain4jToolMapper.arguments(request.arguments()));
                } catch (RuntimeException error) {
                    return new ToolResult(
                            error.getMessage() == null ? "工具执行失败。" : error.getMessage(), true);
                }
            }
            return new ToolResult("未知工具：" + request.name(), true);
        }
        if (!groupManager.isActiveTool(request.name())) {
            return new ToolResult("工具未激活：" + request.name(), true);
        }
        try {
            var result = executor.executeWithContext(request, null);
            return new ToolResult(result.resultText(), result.isError());
        } catch (RuntimeException error) {
            return new ToolResult(error.getMessage() == null ? "工具执行失败。" : error.getMessage(), true);
        }
    }

    private void put(ToolSpecification specification, ToolExecutor executor, String groupName) {
        if (!names.add(specification.name())) {
            throw new IllegalArgumentException("工具名称重复：" + specification.name());
        }
        executors.put(specification, executor);
        namedExecutors.put(specification.name(), executor);
        if (groupName != null) {
            groupManager.addTool(groupName, specification.name());
        }
    }

    private static ToolSpecification specification(Method method) {
        return ToolSpecifications.toolSpecificationFrom(method);
    }

    private static ToolExecutor dynamicExecutor(ToolHandler handler) {
        return new ToolExecutor() {

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return handler.execute(request, LangChain4jToolMapper.arguments(request.arguments())).output();
            }

            @Override
            public ToolExecutionResult executeWithContext(
                    ToolExecutionRequest request, InvocationContext context) {
                var result = handler.execute(request, LangChain4jToolMapper.arguments(request.arguments()));
                return ToolExecutionResult.builder()
                        .resultText(result.output())
                        .isError(result.error())
                        .build();
            }
        };
    }

    private ToolExecutor asyncExecutor(String toolName, ToolHandler handler) {
        return new ToolExecutor() {

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return submitAsync(toolName, request, handler).output();
            }

            @Override
            public ToolExecutionResult executeWithContext(
                    ToolExecutionRequest request, InvocationContext context) {
                var result = submitAsync(toolName, request, handler);
                return ToolExecutionResult.builder()
                        .resultText(result.output())
                        .isError(result.error())
                        .build();
            }
        };
    }

    private ToolResult submitAsync(
            String toolName, ToolExecutionRequest request, ToolHandler handler) {
        if (asyncRegistry == null) {
            return new ToolResult("异步工具注册表未配置。", true);
        }
        try {
            var task = asyncRegistry
                    .submit(
                            toolName,
                            () -> handler.execute(
                                    request, LangChain4jToolMapper.arguments(request.arguments())))
                    .block();
            if (task == null) {
                return new ToolResult("异步任务提交失败。", true);
            }
            return new ToolResult(
                    "工具已在后台执行：" + task.id() + "（工具：" + toolName + "）。", false);
        } catch (RuntimeException error) {
            return new ToolResult(
                    error.getMessage() == null ? "异步任务提交失败。" : error.getMessage(), true);
        }
    }

    @FunctionalInterface
    public interface ToolHandler {

        ToolResult execute(ToolExecutionRequest request, JsonNode arguments);
    }

    public record ToolResult(String output, boolean error) {

        public ToolResult {
            output = output == null ? "" : output;
        }
    }
}