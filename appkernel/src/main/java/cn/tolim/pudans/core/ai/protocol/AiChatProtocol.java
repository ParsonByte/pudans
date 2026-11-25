package cn.tolim.pudans.core.ai.protocol;

import io.agentscope.core.model.Model;

public interface AiChatProtocol extends AiProtocolDescriptor {

    Model create(AiChatProtocolRequest request, AiProtocolProfile profile);

    default Model create(AiChatProtocolRequest request) {
        return create(request, null);
    }
}
