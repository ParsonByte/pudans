package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.ActivitySubmission;
import io.r2dbc.spi.Row;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class ActivitySubmissionRepository {

    private static final String INSERT_SQL = """
            INSERT INTO activity_submissions (
                id, activity_id, work_id, owner_id, workspace_id, status,
                review_note, submitted_at, reviewed_at, created_at, updated_at)
            VALUES (
                :id, :activityId, :workId, :ownerId, :workspaceId, :status,
                :reviewNote, :submittedAt, :reviewedAt, :createdAt, :updatedAt)
            RETURNING id, activity_id, work_id, owner_id, workspace_id, status,
                review_note, submitted_at, reviewed_at, created_at, updated_at
            """;

    private static final String FIND_SQL = """
            SELECT id, activity_id, work_id, owner_id, workspace_id, status,
                review_note, submitted_at, reviewed_at, created_at, updated_at
            FROM activity_submissions
            WHERE activity_id = :activityId AND work_id = :workId
            """;

    private static final String LIST_MINE_SQL = """
            SELECT id, activity_id, work_id, owner_id, workspace_id, status,
                review_note, submitted_at, reviewed_at, created_at, updated_at
            FROM activity_submissions
            WHERE activity_id = :activityId
                AND owner_id = :ownerId AND workspace_id = :workspaceId
            ORDER BY submitted_at DESC, id DESC
            """;

    private final DatabaseClient databaseClient;

    public Mono<ActivitySubmission> insert(ActivitySubmission submission) {
        var spec = databaseClient
                .sql(INSERT_SQL)
                .bind("id", submission.id())
                .bind("activityId", submission.activityId())
                .bind("workId", submission.workId())
                .bind("ownerId", submission.ownerId())
                .bind("workspaceId", submission.workspaceId())
                .bind("status", submission.status())
                .bind("submittedAt", CreationRows.offset(submission.submittedAt()))
                .bind("createdAt", CreationRows.offset(submission.createdAt()))
                .bind("updatedAt", CreationRows.offset(submission.updatedAt()));
        spec = bindNullable(spec, "reviewNote", submission.reviewNote(), String.class);
        spec = bindNullable(
                spec, "reviewedAt", CreationRows.offset(submission.reviewedAt()), OffsetDateTime.class);
        return spec
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<ActivitySubmission> find(UUID activityId, UUID workId) {
        return databaseClient
                .sql(FIND_SQL)
                .bind("activityId", activityId)
                .bind("workId", workId)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<List<ActivitySubmission>> listMine(
            UUID activityId, String ownerId, String workspaceId) {
        return databaseClient
                .sql(LIST_MINE_SQL)
                .bind("activityId", activityId)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> map(row))
                .all()
                .collectList();
    }

    private static ActivitySubmission map(Row row) {
        return new ActivitySubmission(
                CreationRows.uuid(row, "id"),
                CreationRows.uuid(row, "activity_id"),
                CreationRows.uuid(row, "work_id"),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("status", String.class),
                row.get("review_note", String.class),
                CreationRows.instant(row, "submitted_at"),
                CreationRows.instant(row, "reviewed_at"),
                CreationRows.instant(row, "created_at"),
                CreationRows.instant(row, "updated_at"));
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
