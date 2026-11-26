package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiLyricsGenerationResult(
        @JsonProperty("song_title") String songTitle,
        @JsonProperty("style_tags") String styleTags,
        String lyrics) {}
