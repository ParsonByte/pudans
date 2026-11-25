package cn.tolim.pudans.core.agent.runtime;

import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AgentRuntime {

    // 启动一次已持久化的 Agent 运行。
    Mono<Void> start(AgentRunContext context);

    // 请求运行中的 Agent 停止继续执行并返回是否存在活动句柄。
    Mono<Boolean> cancel(AgentRunContext context);

    // 将审批决定交回同一实例持有的 AgentScope 运行态并返回是否恢复。
    Mono<Boolean> resume(AgentRunContext context, UUID approvalId, boolean approved);
}
