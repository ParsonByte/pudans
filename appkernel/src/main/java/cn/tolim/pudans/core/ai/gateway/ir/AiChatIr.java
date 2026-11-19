package cn.tolim.pudans.core.ai.gateway.ir;

import java.util.List;
import java.util.Objects;

public record AiChatIr(
        List<AiChatMessage> messages,
        List<AiTool> tools,
        AiRequestParams requestParams,
        boolean stream) {

    public AiChatIr {
        messages = List.copyOf(Objects.requireNonNull(messages, "消息列表不能为空"));
        tools = List.copyOf(Objects.requireNonNull(tools, "工具列表不能为空"));
        requestParams = Objects.requireNonNull(requestParams, "请求参数不能为空");
    }
}
