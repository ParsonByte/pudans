package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiVideoGenerationPage(List<AiVideoGenerationTask> items, Integer total) {

    public AiVideoGenerationPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
