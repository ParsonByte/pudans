package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;

public interface AiResponseEncoder {

    AiProvider.Protocol protocol();

    String encode(AiChatResponse response, String model);

    AiStreamEncoder stream(String responseId, String model, AiTokenUsage usage);
}
