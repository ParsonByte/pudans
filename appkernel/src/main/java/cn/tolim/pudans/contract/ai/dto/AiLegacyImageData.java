package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiLegacyImageData(
        @JsonProperty("image_urls") List<String> imageUrls,
        @JsonProperty("image_base64") List<String> imageBase64) {

    public AiLegacyImageData {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        imageBase64 = imageBase64 == null ? List.of() : List.copyOf(imageBase64);
    }
}
