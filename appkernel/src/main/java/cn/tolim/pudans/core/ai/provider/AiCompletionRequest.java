package cn.tolim.pudans.core.ai.provider;

import java.util.List;

public record AiCompletionRequest(
        String prompt,
        String suffix,
        Integer maxTokens,
        Double temperature,
        Double topP,
        List<String> stop,
        Boolean echo,
        Integer logprobs) {

    public AiCompletionRequest {
        stop = stop == null ? List.of() : List.copyOf(stop);
    }
}
