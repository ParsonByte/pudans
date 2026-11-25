package cn.tolim.pudans.core.ai.protocol.systemone;

import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.protocol.AiProtocolDescriptor;
import org.springframework.stereotype.Component;

@Component
public class SystemOneProtocol implements AiProtocolDescriptor {

    @Override
    public AiProtocol protocol() {
        return AiProtocol.SYSTEM_ONE;
    }

    @Override
    public String description() {
        return "System One 决策协议，使用状态和 noul/choice/score 问题获取结构化决策。";
    }

    @Override
    public String requestPath() {
        return "/v1/systemone";
    }

    @Override
    public boolean runtimeSupported() {
        return true;
    }
}
