package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record MiniMaxAsrSegment(
        Integer id, Double start, Double end, String speaker, String text) {}
