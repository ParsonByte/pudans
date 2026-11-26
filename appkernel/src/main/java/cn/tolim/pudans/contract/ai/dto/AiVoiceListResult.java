package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVoiceListResult(
        @JsonProperty("system_voice") List<AiVoiceInfo> systemVoices,
        @JsonProperty("voice_cloning") List<AiVoiceInfo> cloningVoices,
        @JsonProperty("voice_generation") List<AiVoiceInfo> generationVoices) {

    public AiVoiceListResult {
        systemVoices = systemVoices == null ? List.of() : List.copyOf(systemVoices);
        cloningVoices = cloningVoices == null ? List.of() : List.copyOf(cloningVoices);
        generationVoices = generationVoices == null ? List.of() : List.copyOf(generationVoices);
    }
}
