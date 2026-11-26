package cn.tolim.pudans.core.ai.provider.deepseek;

import cn.tolim.pudans.core.ai.protocol.messages.MessagesProfile;
import java.util.Map;

record DeepSeekMessagesProfile() implements MessagesProfile {

    @Override
    public Map<String, Object> reasoningBodyParams(
            boolean reasoningSupported, String reasoning) {
        return DeepSeekReasoning.messages(reasoningSupported, reasoning);
    }
}
