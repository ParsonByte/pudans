package cn.tolim.pudans.core.ai.harness.subagent;

import cn.tolim.pudans.core.ai.harness.agent.Agent;
import cn.tolim.pudans.core.ai.harness.agent.AgentResult;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import java.util.UUID;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

record EchoAgentFixture(String name, Sinks.One<String> gate) implements Agent {

    @Override
    public Mono<AgentResult> call(String input, RuntimeContext context) {
        return start(input, context).result();
    }

    @Override
    public AgentRun start(String input, RuntimeContext context) {
        var run = new AgentRun("run-" + UUID.randomUUID());
        var source = gate == null ? Mono.just(input) : gate.asMono();
        run.attach(source
                .map(value -> name + ":" + value)
                .doOnSubscribe(subscription -> run.emit(new AgentEvent.AgentStart(run.id(), name)))
                .map(content -> {
                    run.emit(new AgentEvent.AgentResult(run.id(), content));
                    return new AgentResult(content, null);
                })
                .doFinally(signal -> run.emit(new AgentEvent.AgentEnd(run.id(), name))));
        return run;
    }
}