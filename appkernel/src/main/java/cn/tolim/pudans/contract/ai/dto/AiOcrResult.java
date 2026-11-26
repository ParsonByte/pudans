package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiOcrResult(
        String id,
        String object,
        Long created,
        String purpose,
        @JsonProperty("page_count") Integer pageCount,
        List<AiOcrPage> pages,
        AiOcrUsage usage) {

    public AiOcrResult {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
