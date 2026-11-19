package cn.tolim.pudans.core.ai.gateway.agent;

import cn.tolim.pudans.core.ai.gateway.AiChatRequest;
import java.util.Optional;

public interface AiAgentAdapter {

    String source();

    AiRequestKind classifyRequest(AiChatRequest request);

    Optional<String> extractUserText(AiChatRequest request);
}
