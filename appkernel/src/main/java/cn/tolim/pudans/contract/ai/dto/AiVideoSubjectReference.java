package cn.tolim.pudans.contract.ai.dto;

import java.util.List;

public record AiVideoSubjectReference(String type, List<String> image) {

    public AiVideoSubjectReference {
        image = image == null ? List.of() : List.copyOf(image);
    }
}
