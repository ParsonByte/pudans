package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiSearchResponse(List<AiSearchResult> results) {

    public AiSearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
