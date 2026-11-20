package cn.tolim.pudans.core.ai.harness.tool;

import java.util.List;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface AsyncToolRegistry {

    Mono<AsyncToolTask> submit(String toolName, Supplier<HarnessToolkit.ToolResult> execution);

    Mono<AsyncToolTask> read(String taskId);

    Mono<Boolean> cancel(String taskId);

    Mono<List<AsyncToolTask>> list();
}