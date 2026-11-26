package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiMusicCoverResult(
        @JsonProperty("cover_feature_id") String coverFeatureId,
        @JsonProperty("formatted_lyrics") String formattedLyrics,
        @JsonProperty("structure_result") String structureResult,
        @JsonProperty("audio_duration") Double audioDuration,
        @JsonProperty("trace_id") String traceId) {}
