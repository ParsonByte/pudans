package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.core.ai.protocol.messages.MessagesProfile;
import java.util.Map;

record MiniMaxMessagesProfile() implements MessagesProfile {

    @Override
    public Map<String, Object> reasoningBodyParams(
            boolean reasoningSupported, String reasoning) {
        return MiniMaxReasoning.messages(reasoningSupported, reasoning);
    }
}
