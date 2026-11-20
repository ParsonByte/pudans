package cn.tolim.pudans.infra.r2dbc.ai.journal;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.journal.JournalEvent;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventDraft;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventType;
import cn.tolim.pudans.core.ai.harness.journal.JournalException;
import cn.tolim.pudans.core.ai.harness.journal.JournalHead;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiHarnessJournalRepository {

    private static final String APPEND_SQL = """
            WITH head AS (
                INSERT INTO ai_harness_journal_head (user_id, session_id, last_seq, updated_at)
                SELECT :userId, :sessionId, :count, now()
                WHERE :expectedSeq = 0
                ON CONFLICT (user_id, session_id) DO UPDATE
                SET last_seq = ai_harness_journal_head.last_seq + :count,
                    updated_at = now()
                WHERE ai_harness_journal_head.last_seq = :expectedSeq
                RETURNING last_seq - :count AS first_seq
            ),
            input AS (
                SELECT *
                FROM jsonb_to_recordset(CAST(:events AS jsonb)) AS event (
                    ordinal INT,
                    event_id TEXT,
                    event_type TEXT,
                    run_id TEXT,
                    turn_id TEXT,
                    required BOOLEAN,
                    payload JSONB,
                    occurred_at TEXT
                )
            )
            INSERT INTO ai_harness_journal_event (
                user_id, session_id, seq, event_id, event_type, run_id, turn_id,
                required, payload, occurred_at
            )
            SELECT :userId, :sessionId, head.first_seq + input.ordinal,
                   input.event_id, input.event_type, input.run_id, input.turn_id,
                   input.required, input.payload, CAST(input.occurred_at AS timestamptz)
            FROM head, input
            RETURNING seq, event_id, event_type, run_id, turn_id, required,
                      payload::text AS payload, occurred_at
            """;

    private final DatabaseClient databaseClient;

    public Mono<JournalHead> head(String userId, String sessionId) {
        return databaseClient.sql("""
                        SELECT last_seq
                        FROM ai_harness_journal_head
                        WHERE user_id = :userId AND session_id = :sessionId
                        """)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .map((row, metadata) -> new JournalHead(row.get("last_seq", Long.class)))
                .one()
                .defaultIfEmpty(JournalHead.empty());
    }

    public Mono<List<JournalEvent>> append(
            String userId, String sessionId, long expectedSeq, List<JournalEventDraft> drafts) {
        if (expectedSeq < 0) {
            return Mono.error(new JournalException("线程日志期望序号不能为负数。"));
        }
        if (drafts == null || drafts.isEmpty()) {
            return Mono.just(List.of());
        }
        return databaseClient.sql(APPEND_SQL)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .bind("count", drafts.size())
                .bind("expectedSeq", expectedSeq)
                .bind("events", eventJson(drafts))
                .map((row, metadata) -> mapEvent(row))
                .all()
                .collectSortedList(Comparator.comparingLong(JournalEvent::seq))
                .flatMap(events -> {
                    if (events.size() != drafts.size()) {
                        return Mono.error(new JournalException("线程日志追加序号冲突。"));
                    }
                    return Mono.just(List.copyOf(events));
                });
    }

    public Flux<RuntimeContext> staleActiveThreads(Instant before) {
        return databaseClient.sql("""
                        SELECT DISTINCT e.user_id, e.session_id
                        FROM ai_harness_journal_event e
                        JOIN ai_harness_journal_head h
                          ON h.user_id = e.user_id AND h.session_id = e.session_id
                        WHERE e.event_type = 'RUN_START'
                          AND h.updated_at < :before
                          AND NOT EXISTS (
                              SELECT 1 FROM ai_harness_journal_event end_event
                              WHERE end_event.user_id = e.user_id
                                AND end_event.session_id = e.session_id
                                AND end_event.run_id = e.run_id
                                AND end_event.event_type = 'RUN_END'
                          )
                          AND NOT EXISTS (
                              SELECT 1 FROM ai_harness_journal_event recovery
                              WHERE recovery.user_id = e.user_id
                                AND recovery.session_id = e.session_id
                                AND recovery.event_type = 'RECOVERY_APPLIED'
                                AND jsonb_exists(recovery.payload -> 'interruptedRuns', e.run_id)
                          )
                        """)
                .bind("before", before)
                .map((row, metadata) -> RuntimeContext.of(
                        row.get("user_id", String.class), row.get("session_id", String.class)))
                .all();
    }

    public Flux<JournalEvent> scan(
            String userId, String sessionId, long after, long through) {
        return databaseClient.sql("""
                        SELECT seq, event_id, event_type, run_id, turn_id, required,
                               payload::text AS payload, occurred_at
                        FROM ai_harness_journal_event
                        WHERE user_id = :userId
                          AND session_id = :sessionId
                          AND seq > :after
                          AND seq <= :through
                        ORDER BY seq
                        """)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .bind("after", after)
                .bind("through", through)
                .map((row, metadata) -> mapEvent(row))
                .all();
    }

    private static JournalEvent mapEvent(Row row) {
        return new JournalEvent(
                row.get("event_id", String.class),
                row.get("seq", Long.class),
                row.get("occurred_at", Instant.class),
                JournalEventType.parse(row.get("event_type", String.class)),
                row.get("run_id", String.class),
                row.get("turn_id", String.class),
                Boolean.TRUE.equals(row.get("required", Boolean.class)),
                AiR2dbcBindings.jsonText(row.get("payload")));
    }

    private static String eventJson(List<JournalEventDraft> drafts) {
        var events = JsonUtils.mapper().createArrayNode();
        for (var index = 0; index < drafts.size(); index++) {
            var draft = drafts.get(index);
            var event = events.addObject();
            event.put("ordinal", index + 1);
            event.put("event_id", draft.eventId());
            event.put("event_type", draft.type().name());
            putNullable(event, "run_id", draft.runId());
            putNullable(event, "turn_id", draft.turnId());
            event.put("required", draft.required());
            event.set("payload", JsonUtils.mapper().valueToTree(draft.payload()));
            event.put("occurred_at", draft.occurredAt().toString());
        }
        return events.toString();
    }

    private static void putNullable(ObjectNode node, String name, @Nullable String value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value);
        }
    }
}
