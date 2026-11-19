package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;

public interface AiProtocolCodec {

    AiProvider.Protocol protocol();

    AiChatIr parse(String body);

    String serialize(AiChatIr ir);
}
