package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiFilePage(
        String object,
        List<AiFile> data,
        @JsonProperty("first_id") String firstId,
        @JsonProperty("last_id") String lastId,
        @JsonProperty("has_more") boolean hasMore) {

    public AiFilePage {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
