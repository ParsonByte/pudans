package cn.tolim.pudans.infra.r2dbc.ai.skill;

import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageRecord;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiSkillUsageRepository {

    private static final String COLUMNS = "skill_name, created_by, use_count, view_count, patch_count, state, "
            + "promoted_by, environments, created_at, last_activity_at";

    private final DatabaseClient databaseClient;

    public Mono<NamedUsage> find(String name) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM ai_skill_usage WHERE skill_name = :name")
                .bind("name", name)
                .map((row, metadata) -> named(row))
                .one();
    }

    public Flux<NamedUsage> list() {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_skill_usage
                         ORDER BY last_activity_at DESC NULLS LAST, skill_name
                         """)
                .map((row, metadata) -> named(row))
                .all();
    }

    public Mono<NamedUsage> recordView(String name) {
        return increment(name, "view_count");
    }

    public Mono<NamedUsage> recordUse(String name) {
        return increment(name, "use_count");
    }

    public Mono<NamedUsage> recordPatch(String name) {
        return increment(name, "patch_count");
    }

    private Mono<NamedUsage> increment(String name, String column) {
        return databaseClient.sql("""
                        INSERT INTO ai_skill_usage (skill_name, %s, last_activity_at)
                        VALUES (:name, 1, now())
                        ON CONFLICT (skill_name) DO UPDATE
                        SET %s = ai_skill_usage.%s + 1,
                            last_activity_at = now()
                        RETURNING
                        """ .formatted(column, column, column) + COLUMNS)
                .bind("name", name)
                .map((row, metadata) -> named(row))
                .one();
    }

    public Mono<NamedUsage> markPromoted(
            String name, String reviewerId, List<String> environments) {
        var envs = environments == null || environments.isEmpty()
                ? new String[0]
                : environments.toArray(String[]::new);
        return databaseClient.sql("""
                        INSERT INTO ai_skill_usage (
                            skill_name, created_by, state, promoted_by, environments, last_activity_at
                        ) VALUES (:name, 'agent', 'ACTIVE', :reviewer, :environments, now())
                        ON CONFLICT (skill_name) DO UPDATE
                        SET state = 'ACTIVE',
                            promoted_by = EXCLUDED.promoted_by,
                            environments = EXCLUDED.environments,
                            last_activity_at = now()
                        RETURNING
                        """ + COLUMNS)
                .bind("name", name)
                .bind("reviewer", reviewerId)
                .bind("environments", envs)
                .map((row, metadata) -> named(row))
                .one();
    }

    public Mono<NamedUsage> setState(String name, String state) {
        return databaseClient.sql("UPDATE ai_skill_usage SET state = :state WHERE skill_name = :name RETURNING "
                        + COLUMNS)
                .bind("name", name)
                .bind("state", state)
                .map((row, metadata) -> named(row))
                .one();
    }

    private static NamedUsage named(Row row) {
        return new NamedUsage(row.get("skill_name", String.class), map(row));
    }

    static SkillUsageRecord map(Row row) {
        return new SkillUsageRecord(
                row.get("created_by", String.class),
                number(row.get("use_count", Long.class)),
                number(row.get("view_count", Long.class)),
                number(row.get("patch_count", Long.class)),
                row.get("created_at", Instant.class),
                row.get("last_activity_at", Instant.class),
                state(row.get("state", String.class)),
                row.get("promoted_by", String.class),
                strings(row.get("environments", String[].class)));
    }

    private static long number(@Nullable Long value) {
        return value == null ? 0L : value;
    }

    private static SkillUsageRecord.State state(@Nullable String value) {
        try {
            return value == null ? SkillUsageRecord.State.ACTIVE : SkillUsageRecord.State.valueOf(value);
        } catch (IllegalArgumentException error) {
            return SkillUsageRecord.State.ACTIVE;
        }
    }

    private static List<String> strings(@Nullable String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    public record NamedUsage(String name, SkillUsageRecord record) {}
}