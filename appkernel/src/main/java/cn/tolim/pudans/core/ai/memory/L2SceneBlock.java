package cn.tolim.pudans.core.ai.memory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record L2SceneBlock(
        Long id,
        String teamId,
        String agentId,
        String sceneKey,
        String title,
        String summary,
        List<Long> l1Ids,
        int heat,
        BigDecimal confidence,
        Instant lastActiveAt) {

    public L2SceneBlock {
        requireText(teamId, "teamId");
        requireText(agentId, "agentId");
        requireText(sceneKey, "sceneKey");
        requireText(title, "title");
        requireText(summary, "summary");
        l1Ids = l1Ids == null ? List.of() : List.copyOf(l1Ids);
        if (l1Ids.isEmpty()) {
            throw new IllegalArgumentException("l1Ids 不能为空");
        }
        if (heat < 0) {
            throw new IllegalArgumentException("heat 不能为负数");
        }
        Objects.requireNonNull(confidence, "confidence");
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
        }
    }

    public L2SceneBlock merge(L2SceneBlock incoming, Instant now) {
        Objects.requireNonNull(incoming, "incoming");
        var mergedIds = new LinkedHashSet<Long>(l1Ids);
        mergedIds.addAll(incoming.l1Ids);
        return new L2SceneBlock(
                id,
                teamId,
                agentId,
                sceneKey,
                incoming.title,
                incoming.summary,
                List.copyOf(mergedIds),
                heat + 1,
                incoming.confidence,
                now);
    }

    public L2SceneBlock withId(Long id) {
        return new L2SceneBlock(id, teamId, agentId, sceneKey, title, summary, l1Ids, heat, confidence, lastActiveAt);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
