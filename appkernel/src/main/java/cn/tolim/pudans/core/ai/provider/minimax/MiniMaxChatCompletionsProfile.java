package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.core.ai.protocol.chatcompletions.ChatCompletionsProfile;
import java.util.Map;

record MiniMaxChatCompletionsProfile() implements ChatCompletionsProfile {

    @Override
    public Map<String, Object> reasoningBodyParams(
            boolean reasoningSupported, String reasoning) {
        return MiniMaxReasoning.chat(reasoningSupported, reasoning);
    }
}
