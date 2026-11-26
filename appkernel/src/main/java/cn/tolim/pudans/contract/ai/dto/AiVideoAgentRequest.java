package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiVideoAgentRequest(
        @JsonProperty("template_id") String templateId,
        @JsonProperty("text_inputs") List<AiVideoAgentInput> textInputs,
        @JsonProperty("media_inputs") List<AiVideoAgentInput> mediaInputs,
        @JsonProperty("callback_url") String callbackUrl) {

    public AiVideoAgentRequest {
        textInputs = textInputs == null ? List.of() : List.copyOf(textInputs);
        mediaInputs = mediaInputs == null ? List.of() : List.copyOf(mediaInputs);
    }
}
