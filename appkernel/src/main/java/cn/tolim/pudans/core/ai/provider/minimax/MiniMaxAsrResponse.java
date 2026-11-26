package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record MiniMaxAsrResponse(
        String text,
        Double duration,
        @JsonProperty("n_speakers") Integer speakers,
        List<MiniMaxAsrSegment> segments,
        @JsonProperty("trace_id") String traceId) {

    MiniMaxAsrResponse {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
