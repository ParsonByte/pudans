package cn.tolim.pudans.core.ai.memory.org;

import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.memory.SceneSegment;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OrgCandidatePolicy {

    private static final String ORG_CANDIDATE_METADATA = "orgCandidate";

    private static final int MIN_DURABLE_PRIORITY = 70;

    private static final Set<MemoryType> PERSONAL_TYPES =
            EnumSet.of(MemoryType.PERSONA, MemoryType.EPISODIC, MemoryType.WORK_TASK);

    private OrgCandidatePolicy() {}

    public static boolean hasCandidate(List<SceneSegment> scenes) {
        if (scenes == null) {
            return false;
        }
        for (var scene : scenes) {
            for (var memory : scene.memories()) {
                if (isCandidate(memory)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isCandidate(ExtractedMemory memory) {
        if (memory == null || PERSONAL_TYPES.contains(memory.type())) {
            return false;
        }
        return flagged(memory.metadata()) || isDurableType(memory);
    }

    private static boolean isDurableType(ExtractedMemory memory) {
        return memory.priority() >= MIN_DURABLE_PRIORITY
                && (memory.type() == MemoryType.INSTRUCTION || memory.type() == MemoryType.WORK_METHOD);
    }

    private static boolean flagged(Map<String, Object> metadata) {
        var value = metadata.get(ORG_CANDIDATE_METADATA);
        return Boolean.TRUE.equals(value) || (value instanceof String text && Boolean.parseBoolean(text));
    }
}
