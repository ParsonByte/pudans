package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiVoiceInfo(
        @JsonProperty("voice_id") String voiceId,
        @JsonProperty("voice_name") String voiceName,
        List<String> description,
        @JsonProperty("created_time") String createdTime) {

    public AiVoiceInfo {
        description = description == null ? List.of() : List.copyOf(description);
    }
}
