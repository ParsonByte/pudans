package cn.tolim.pudans.core.ai.provider.deepseek;

import cn.tolim.pudans.core.ai.protocol.responses.ResponsesProfile;

record DeepSeekResponsesProfile() implements ResponsesProfile {

    @Override
    public String reasoningEffort(boolean reasoningSupported, String reasoning) {
        return DeepSeekReasoning.responses(reasoningSupported, reasoning);
    }
}
