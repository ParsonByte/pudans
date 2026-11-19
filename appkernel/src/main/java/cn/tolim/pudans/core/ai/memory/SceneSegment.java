package cn.tolim.pudans.core.ai.memory;

import java.util.List;

public record SceneSegment(String sceneName, List<String> messageIds, List<ExtractedMemory> memories) {

    public SceneSegment {
        if (sceneName == null || sceneName.isBlank()) {
            throw new IllegalArgumentException("sceneName 不能为空");
        }
        messageIds = messageIds == null ? List.of() : List.copyOf(messageIds);
        memories = memories == null ? List.of() : List.copyOf(memories);
    }
}
