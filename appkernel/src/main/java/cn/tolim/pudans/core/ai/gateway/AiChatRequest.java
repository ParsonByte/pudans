package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import java.util.Objects;

public record AiChatRequest(AiProvider.Protocol protocol, AiChatIr ir) {

    public AiChatRequest {
        protocol = Objects.requireNonNull(protocol, "协议不能为空");
        ir = Objects.requireNonNull(ir, "聊天 IR 不能为空");
    }
}
