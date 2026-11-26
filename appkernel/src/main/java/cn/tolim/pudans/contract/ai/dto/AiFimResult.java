package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiFimResult(
        String id,
        List<AiFimChoice> choices,
        Long created,
        String model,
        @JsonProperty("system_fingerprint") String systemFingerprint,
        String object,
        AiFimUsage usage) {

    public AiFimResult {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
