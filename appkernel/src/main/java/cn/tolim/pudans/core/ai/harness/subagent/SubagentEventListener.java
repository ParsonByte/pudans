package cn.tolim.pudans.core.ai.harness.subagent;

import cn.tolim.pudans.core.ai.harness.event.AgentEvent;

@FunctionalInterface
public interface SubagentEventListener {

    void onEvent(String agentKey, AgentEvent event);
}