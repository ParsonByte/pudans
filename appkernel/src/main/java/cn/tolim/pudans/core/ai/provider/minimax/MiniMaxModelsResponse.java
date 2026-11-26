package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiniMaxModelsResponse(List<MiniMaxModel> data) {

    public MiniMaxModelsResponse {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
