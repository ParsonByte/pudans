package cn.tolim.pudans.infra.r2dbc.ai.team;

import cn.tolim.pudans.core.ai.harness.team.TeamTask;
import cn.tolim.pudans.core.ai.harness.team.TeamTaskState;
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
public class AiTeamTaskRepository {

    private static final String COLUMNS =
            "task_id, team_name, subject, description, state, owner, blocked_by, result, version, "
                    + "created_at, updated_at";

    private final DatabaseClient databaseClient;

    public Mono<TeamTask> insert(TeamTask task) {
        return databaseClient.sql("""
                        INSERT INTO ai_team_task (
                            task_id, team_name, subject, description, state, owner, blocked_by,
                            result, version, created_at, updated_at
                        ) VALUES (
                            :taskId, :teamName, :subject, :description, :state, :owner, :blockedBy,
                            :result, :version, :createdAt, :updatedAt
                        )
                        RETURNING
                        """ + COLUMNS)
                .bind("taskId", task.taskId())
                .bind("teamName", task.teamName())
                .bind("subject", task.subject())
                .bind("description", task.description())
                .bind("state", task.state().name())
                .bind("owner", task.owner())
                .bind("blockedBy", task.blockedBy().toArray(String[]::new))
                .bind("result", task.result())
                .bind("version", task.version())
                .bind("createdAt", java.time.OffsetDateTime.ofInstant(task.createdAt(), java.time.ZoneOffset.UTC))
                .bind("updatedAt", java.time.OffsetDateTime.ofInstant(task.updatedAt(), java.time.ZoneOffset.UTC))
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<TeamTask> find(String teamName, String taskId) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_team_task
                         WHERE team_name = :teamName AND task_id = :taskId
                         """)
                .bind("teamName", teamName)
                .bind("taskId", taskId)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<TeamTask> list(String teamName) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_team_task
                         WHERE team_name = :teamName
                         ORDER BY created_at, task_id
                         """)
                .bind("teamName", teamName)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<TeamTask> claimable(String teamName, String memberName) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_team_task t
                         WHERE t.team_name = :teamName
                           AND t.state = 'PENDING'
                           AND (t.owner = '' OR t.owner = :memberName)
                           AND NOT EXISTS (
                               SELECT 1 FROM ai_team_task b
                               WHERE b.team_name = t.team_name
                                 AND b.task_id = ANY(t.blocked_by)
                                 AND b.state <> 'COMPLETED'
                           )
                         ORDER BY t.created_at, t.task_id
                         """)
                .bind("teamName", teamName)
                .bind("memberName", memberName)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<TeamTask> update(
            String teamName,
            String taskId,
            long expectedVersion,
            TeamTaskState state,
            String owner,
            String result) {
        return databaseClient.sql("""
                        UPDATE ai_team_task
                        SET state = :state,
                            owner = :owner,
                            result = :result,
                            version = version + 1,
                            updated_at = now()
                        WHERE team_name = :teamName
                          AND task_id = :taskId
                          AND version = :expectedVersion
                        RETURNING
                        """ + COLUMNS)
                .bind("teamName", teamName)
                .bind("taskId", taskId)
                .bind("expectedVersion", expectedVersion)
                .bind("state", state.name())
                .bind("owner", owner)
                .bind("result", result == null ? "" : result)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<Long> countExisting(String teamName, List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Mono.just(0L);
        }
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM ai_team_task
                        WHERE team_name = :teamName AND task_id = ANY(:taskIds)
                        """)
                .bind("teamName", teamName)
                .bind("taskIds", taskIds.toArray(String[]::new))
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    public Mono<Long> unfinishedDependencies(String teamName, List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Mono.just(0L);
        }
        return databaseClient.sql("""
                        SELECT COUNT(*) AS total
                        FROM ai_team_task
                        WHERE team_name = :teamName
                          AND task_id = ANY(:taskIds)
                          AND state <> 'COMPLETED'
                        """)
                .bind("teamName", teamName)
                .bind("taskIds", taskIds.toArray(String[]::new))
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private static TeamTask map(Row row) {
        return new TeamTask(
                row.get("task_id", String.class),
                row.get("team_name", String.class),
                row.get("subject", String.class),
                row.get("description", String.class),
                TeamTaskState.valueOf(row.get("state", String.class)),
                row.get("owner", String.class),
                strings(row.get("blocked_by", String[].class)),
                row.get("result", String.class),
                row.get("version", Long.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private static List<String> strings(@Nullable String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }
}