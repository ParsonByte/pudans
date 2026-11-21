package cn.tolim.pudans.core.infra.r2dbc.ai.tool;

import cn.tolim.pudans.core.ai.harness.tool.AsyncToolStatus;
import cn.tolim.pudans.core.ai.harness.tool.AsyncToolTask;
import io.r2dbc.spi.Row;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiAsyncToolTaskRepository {

    private static final String COLUMNS = "task_id, tool_name, status, output, error";

    private final DatabaseClient databaseClient;

    public Mono<Void> insert(String taskId, String scope, String toolName) {
        return databaseClient.sql("""
                        INSERT INTO ai_async_tool_task (task_id, scope, tool_name, status)
                        VALUES (:taskId, :scope, :toolName, 'RUNNING')
                        ON CONFLICT (task_id) DO NOTHING
                        """)
                .bind("taskId", taskId)
                .bind("scope", scope)
                .bind("toolName", toolName)
                .then();
    }

    public Mono<Void> update(String taskId, AsyncToolStatus status, String output, String error) {
        return databaseClient.sql("""
                        UPDATE ai_async_tool_task
                        SET status = :status, output = :output, error = :error, updated_at = now()
                        WHERE task_id = :taskId
                        """)
                .bind("taskId", taskId)
                .bind("status", status.name())
                .bind("output", output == null ? "" : output)
                .bind("error", error == null ? "" : error)
                .then();
    }

    public Mono<AsyncToolTask> find(String scope, String taskId) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_async_tool_task
                         WHERE task_id = :taskId AND scope = :scope
                         """)
                .bind("taskId", taskId)
                .bind("scope", scope)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<AsyncToolTask> listByScope(String scope) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_async_tool_task
                         WHERE scope = :scope
                         ORDER BY created_at, task_id
                         """)
                .bind("scope", scope)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<Boolean> cancel(String scope, String taskId) {
        return databaseClient.sql("""
                        UPDATE ai_async_tool_task
                        SET status = 'CANCELLED', updated_at = now()
                        WHERE task_id = :taskId AND scope = :scope AND status = 'RUNNING'
                        """)
                .bind("taskId", taskId)
                .bind("scope", scope)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    private static AsyncToolTask map(Row row) {
        var status = row.get("status", String.class);
        return new AsyncToolTask(
                row.get("task_id", String.class),
                row.get("tool_name", String.class),
                status == null ? AsyncToolStatus.FAILED : AsyncToolStatus.valueOf(status),
                row.get("output", String.class),
                row.get("error", String.class));
    }
}