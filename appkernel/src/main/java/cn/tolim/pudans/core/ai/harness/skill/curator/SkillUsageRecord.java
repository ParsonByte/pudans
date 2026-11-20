package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record SkillUsageRecord(
        @Nullable String createdBy,
        long useCount,
        long viewCount,
        long patchCount,
        Instant createdAt,
        @Nullable Instant lastActivityAt,
        State state,
        @Nullable String promotedBy,
        List<String> environments) {

    public enum State {
        ACTIVE,
        ARCHIVED
    }

    public SkillUsageRecord {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        state = state == null ? State.ACTIVE : state;
        environments = environments == null ? List.of() : List.copyOf(environments);
    }

    public static SkillUsageRecord defaults() {
        return new SkillUsageRecord(null, 0L, 0L, 0L, Instant.now(), null, State.ACTIVE, null, List.of());
    }

    public SkillUsageRecord view(Instant now) {
        return new SkillUsageRecord(
                createdBy, useCount, viewCount + 1, patchCount, createdAt, now, state, promotedBy, environments);
    }

    public SkillUsageRecord use(Instant now) {
        return new SkillUsageRecord(
                createdBy, useCount + 1, viewCount, patchCount, createdAt, now, state, promotedBy, environments);
    }

    public SkillUsageRecord patch(Instant now) {
        return new SkillUsageRecord(
                createdBy, useCount, viewCount, patchCount + 1, createdAt, now, state, promotedBy, environments);
    }

    public SkillUsageRecord promoted(String reviewerId, List<String> environments, Instant now) {
        return new SkillUsageRecord(
                "agent",
                useCount,
                viewCount,
                patchCount,
                createdAt,
                now,
                State.ACTIVE,
                reviewerId,
                environments);
    }

    public SkillUsageRecord withState(State newState) {
        return new SkillUsageRecord(
                createdBy, useCount, viewCount, patchCount, createdAt, lastActivityAt, newState, promotedBy, environments);
    }
}