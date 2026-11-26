package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiSearchRequest(
        String query,
        Integer count,
        @JsonProperty("ori_question") String originalQuestion,
        String localization,
        @JsonProperty("need_content") Boolean needContent) {}
