package cn.tolim.pudans.core.ai.harness.agent;

import reactor.core.publisher.Mono;

public interface Agent {

    String name();

    Mono<AgentResult> call(String input, RuntimeContext context);

    AgentRun start(String input, RuntimeContext context);
}