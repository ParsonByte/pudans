package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiImageInput(@JsonProperty("image_url") String imageUrl) {}
