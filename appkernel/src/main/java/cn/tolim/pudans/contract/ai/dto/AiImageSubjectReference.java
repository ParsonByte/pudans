package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiImageSubjectReference(
        String type, @JsonProperty("image_file") String imageFile) {}
