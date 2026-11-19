package cn.tolim.pudans.core.ai.memory;

import java.util.Objects;

public record JevDecision(JevDecisionType decision, JevChangeType changeType, String reason) {

    public JevDecision {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(reason, "reason");
    }

    public boolean shouldGenerate() {
        return decision == JevDecisionType.IMMEDIATE;
    }
}
