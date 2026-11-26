package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record MiniMaxFileListResponse(List<MiniMaxFile> files) {

    MiniMaxFileListResponse {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
