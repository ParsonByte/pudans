package cn.tolim.pudans.core.ai.gateway.ir;

import java.util.List;
import java.util.Objects;

public record AiChatMessage(String role, List<AiContentPart> parts) {

    public AiChatMessage {
        role = Objects.requireNonNull(role, "消息角色不能为空");
        parts = List.copyOf(Objects.requireNonNull(parts, "消息内容不能为空"));
    }
}
