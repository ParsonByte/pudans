package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.core.ai.protocol.responses.ResponsesProfile;

record MiniMaxResponsesProfile() implements ResponsesProfile {

    @Override
    public String reasoningEffort(boolean reasoningSupported, String reasoning) {
        return MiniMaxReasoning.responses(reasoningSupported, reasoning);
    }
}
