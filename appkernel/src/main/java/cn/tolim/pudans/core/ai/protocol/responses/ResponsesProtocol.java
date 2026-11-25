package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocol;
import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;

@Component
public class ResponsesProtocol implements AiChatProtocol {

    @Override
    public AiProtocol protocol() {
        return AiProtocol.RESPONSES;
    }

    @Override
    public String description() {
        return "OpenAI Responses 线协议，支持结构化输入、函数工具和响应事件流。";
    }

    @Override
    public String requestPath() {
        return "/responses";
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }

    @Override
    public Model create(AiChatProtocolRequest request, AiProtocolProfile profile) {
        return new OpenAiResponsesModel(request, profile);
    }
}
