package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTranscriptionSegment(
        Integer id, Double start, Double end, String speaker, String text) {}
