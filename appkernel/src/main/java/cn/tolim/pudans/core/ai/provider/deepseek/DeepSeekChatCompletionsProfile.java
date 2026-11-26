package cn.tolim.pudans.core.ai.provider.deepseek;

import cn.tolim.pudans.core.ai.protocol.chatcompletions.ChatCompletionsProfile;
import java.util.Map;

record DeepSeekChatCompletionsProfile() implements ChatCompletionsProfile {

    @Override
    public Map<String, Object> reasoningBodyParams(
            boolean reasoningSupported, String reasoning) {
        return DeepSeekReasoning.chat(reasoningSupported, reasoning);
    }
}
