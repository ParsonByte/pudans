package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AiSearchResult(
        @JsonProperty("rerank_score") Double rerankScore,
        String title,
        String url,
        String snippet,
        String summary,
        String content,
        @JsonProperty("site_name") String siteName,
        @JsonProperty("site_icon") String siteIcon,
        Instant date,
        Integer authority) {}
