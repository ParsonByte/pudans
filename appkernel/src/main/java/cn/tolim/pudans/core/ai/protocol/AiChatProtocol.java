package cn.tolim.pudans.core.ai.protocol;

import dev.langchain4j.model.chat.StreamingChatModel;

public interface AiChatProtocol extends AiProtocolDescriptor {

    StreamingChatModel create(AiChatProtocolRequest request, AiProtocolProfile profile);

    default StreamingChatModel create(AiChatProtocolRequest request) {
        return create(request, null);
    }
}