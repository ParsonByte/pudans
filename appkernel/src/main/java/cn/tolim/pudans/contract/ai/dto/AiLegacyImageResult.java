package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiLegacyImageResult(
        String id, AiLegacyImageData data, AiLegacyImageMetadata metadata) {}
