package cn.tolim.pudans.core.ai.harness.subagent;

import cn.tolim.pudans.core.ai.harness.agent.Agent;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;

@FunctionalInterface
public interface SubagentFactory {

    Agent create(RuntimeContext parentContext);
}