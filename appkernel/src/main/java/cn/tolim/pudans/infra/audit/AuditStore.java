package cn.tolim.pudans.infra.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.audit.AuditRecord;

@Slf4j
@Component
public class AuditStore {

    private static final int ARCHIVE_BATCH_SIZE = 500;

    private static final int MAX_CHAIN_SIZE = 2_000;

    private static final int MAX_GRAPH_SIZE = 10_000;

    private static final int MAX_TRACE_GROUP_RECORDS = 20_000;

    public static final int MAX_PAGE_RECORDS = 10_000;

    private static final String NON_STATIC_REQUEST_SQL = """
            AND (request_path IS NULL
                 OR (request_path NOT LIKE '/assets/%'
                     AND request_path NOT LIKE '/brand/%'
                     AND request_path NOT LIKE '/webjars/%'
                     AND request_path <> '/favicon.ico'
                     AND request_path <> '/login-bg.mp4'))
            """;

    private final AuditRecordRepository activeRepository;

    private final AuditArchiveRepository archiveRepository;

    private final AuditArchiveGuardRepository guardRepository;

    private final R2dbcEntityOperations entityOperations;

    private final DatabaseClient databaseClient;

    private final TransactionalOperator transactionalOperator;

    // 注入审计仓储与事务操作器，供归档事务复用。
    public AuditStore(
            AuditRecordRepository activeRepository,
            AuditArchiveRepository archiveRepository,
            AuditArchiveGuardRepository guardRepository,
            R2dbcEntityOperations entityOperations,
            DatabaseClient databaseClient,
            ReactiveTransactionManager reactiveTransactionManager) {
        this.activeRepository = activeRepository;
        this.archiveRepository = archiveRepository;
        this.guardRepository = guardRepository;
        this.entityOperations = entityOperations;
        this.databaseClient = databaseClient;
        this.transactionalOperator = TransactionalOperator.create(reactiveTransactionManager);
    }

    // 按指纹去重后写入审计，唯一索引兜底并发重复。
    public Mono<Void> append(AuditRecord record) {
        var fingerprint = record.getFingerprint() == null ? AuditFingerprint.of(record) : record.getFingerprint();
        record.setFingerprint(fingerprint);
        return activeRepository
                .existsByFingerprint(fingerprint)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.just(true)
                        : archiveRepository.existsByFingerprint(fingerprint))
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.empty()
                        : entityOperations.insert(AuditRecordEntity.from(record)).then())
                // 并发下由唯一索引兜底，重复指纹视为已记录。
                .onErrorResume(DuplicateKeyException.class, ignored -> Mono.empty())
                .doOnError(e -> log.error("追加审计记录 {} 失败", record.getMetadata().getName(), e))
                .then();
    }

    // 合并活跃与归档记录，去重后按游标方向排序。
    public Flux<AuditRecord> query(AuditQuery query) {
        var direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(0, Math.max(1, query.limit()), Sort.by(direction, "id"));
        var dataQuery = Query.query(criteria(query)).with(pageable);
        var active = entityOperations
                .select(dataQuery, AuditRecordEntity.class)
                .map(entity -> entity.toRecord(false));
        var archived = entityOperations
                .select(dataQuery, AuditArchiveEntity.class)
                .map(AuditArchiveEntity::toRecord);
        return Flux.merge(active, archived)
                .distinct(record -> record.getMetadata().getName())
                .sort(recordOrder(query.ascending()))
                .take(query.limit());
    }

    // 通过受限深度扫描实现分页，避免加载整表。
    public Mono<AuditPageResult> page(AuditQuery query, int page, int size) {
        var direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        // 限制单次拉取深度，避免过大的页码导致全表加载。
        var fetchLimit = (int) Math.min(MAX_PAGE_RECORDS, (long) page * size);
        var pageable = PageRequest.of(0, Math.max(1, fetchLimit), Sort.by(direction, "id"));
        var items = selectBoth(criteria(query), pageable)
                .sort(recordOrder(query.ascending()))
                .collectList()
                .map(records -> records.stream()
                        .skip((long) (page - 1) * size)
                        .limit(size)
                        .toList());
        return Mono.zip(items, countMatching(query))
                .map(tuple -> new AuditPageResult(tuple.getT1(), tuple.getT2()));
    }

    // 仅在归档表中分页查询，供归档列表接口使用。
    public Mono<AuditPageResult> archivePage(AuditQuery query, int page, int size) {
        var direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(
                Math.max(0, page - 1), Math.max(1, size), Sort.by(direction, "id"));
        var criteria = criteria(query);
        var items = entityOperations
                .select(Query.query(criteria).with(pageable), AuditArchiveEntity.class)
                .map(AuditArchiveEntity::toRecord)
                .collectList();
        var total = entityOperations
                .select(Query.query(criteria), AuditArchiveEntity.class)
                .count();
        return Mono.zip(items, total)
                .map(tuple -> new AuditPageResult(tuple.getT1(), tuple.getT2()));
    }

    // 统计活跃与已归档的非静态请求记录数量。
    public Mono<ArchiveStats> archiveStats() {
        var query = Query.query(nonStaticRequests());
        return Mono.zip(
                        entityOperations.select(query, AuditRecordEntity.class).count(),
                        entityOperations.select(query, AuditArchiveEntity.class).count())
                .map(tuple -> new ArchiveStats(tuple.getT1(), tuple.getT2()));
    }

    // 汇总追踪流并分页返回，结果过大时标记截断。
    public Mono<TraceFlowPage> traceFlows(AuditQuery query, int page, int size) {
        var pageable = PageRequest.of(
                0, MAX_TRACE_GROUP_RECORDS, Sort.by(Sort.Direction.DESC, "id"));
        return selectBoth(criteria(query), pageable)
                .filter(record -> StringUtils.hasText(record.getTraceFlowId()))
                .collectList()
                .map(records -> {
                    var lastByFlow = records.stream()
                            .collect(Collectors.toMap(
                                    AuditRecord::getTraceFlowId,
                                    record -> record.getTimestamp() == null
                                            ? Instant.EPOCH
                                            : record.getTimestamp(),
                                    (left, right) -> left.isAfter(right) ? left : right));
                    var flowIds = lastByFlow.entrySet().stream()
                            .sorted(Map.Entry.<String, Instant>comparingByValue()
                                    .reversed()
                                    .thenComparing(Map.Entry.comparingByKey()))
                            .map(Map.Entry::getKey)
                            .toList();
                    var from = (int) Math.min(flowIds.size(), (long) (page - 1) * size);
                    var to = Math.min(flowIds.size(), from + size);
                    return new TraceFlowPage(
                            flowIds.subList(from, to),
                            flowIds.size(),
                            records.size() >= MAX_TRACE_GROUP_RECORDS);
                });
    }

    // 统计活跃与归档表中满足条件的记录总数。
    private Mono<Long> countMatching(AuditQuery query) {
        var criteria = criteria(query);
        var active = entityOperations
                .select(Query.query(criteria), AuditRecordEntity.class)
                .count();
        var archived = entityOperations
                .select(Query.query(criteria), AuditArchiveEntity.class)
                .count();
        return Mono.zip(active, archived).map(tuple -> tuple.getT1() + tuple.getT2());
    }

    // 按审计标识生成升降序比较器。
    private static Comparator<AuditRecord> recordOrder(boolean ascending) {
        Comparator<AuditRecord> order = Comparator.comparing(record -> record.getMetadata().getName());
        return ascending ? order : order.reversed();
    }

    // 组合游标、时间与业务筛选条件，并排除静态资源。
    private static Criteria criteria(AuditQuery query) {
        // 先放置恒真条件，确保 nonStaticRequests 的 OR 被整体括起后再与筛选条件合并。
        var criteria = Criteria.where("id").isNotNull().and(nonStaticRequests());
        if (StringUtils.hasText(query.cursor())) {
            criteria = query.ascending()
                    ? criteria.and("id").greaterThan(query.cursor())
                    : criteria.and("id").lessThan(query.cursor());
        }
        if (query.from() != null) {
            criteria = criteria.and("occurredAt").greaterThanOrEquals(toOffsetDateTime(query.from()));
        }
        if (query.to() != null) {
            criteria = criteria.and("occurredAt").lessThan(toOffsetDateTime(query.to()));
        }
        if (StringUtils.hasText(query.uid())) {
            criteria = criteria.and("uid").is(query.uid());
        }
        if (StringUtils.hasText(query.operation())) {
            criteria = criteria.and("operation").is(query.operation());
        }
        if (StringUtils.hasText(query.result())) {
            criteria = criteria.and("result").is(query.result());
        }
        if (StringUtils.hasText(query.traceId())) {
            criteria = criteria.and("traceId").is(query.traceId());
        }
        if (StringUtils.hasText(query.traceFlowId())) {
            criteria = criteria.and("traceFlowId").is(query.traceFlowId());
        }
        return criteria;
    }

    // 排除静态资源请求，只保留有业务意义的审计。
    private static Criteria nonStaticRequests() {
        return Criteria.where("requestPath")
                .isNull()
                .or(Criteria.where("requestPath")
                        .notLike("/assets/%")
                        .and("requestPath")
                        .notLike("/brand/%")
                        .and("requestPath")
                        .notLike("/webjars/%")
                        .and("requestPath")
                        .not("/favicon.ico")
                        .and("requestPath")
                        .not("/login-bg.mp4"));
    }

    // 先查活跃表，未命中时回退到归档表。
    public Mono<AuditRecord> findById(String auditId) {
        return activeRepository
                .findById(auditId)
                .map(entity -> entity.toRecord(false))
                .switchIfEmpty(archiveRepository.findById(auditId).map(AuditArchiveEntity::toRecord));
    }

    // 统计指定时间之后的非静态请求审计数量。
    public Mono<Long> countSince(Instant from) {
        var criteria = Criteria.where("occurredAt")
                .greaterThanOrEquals(toOffsetDateTime(from))
                .and(nonStaticRequests());
        var active = entityOperations
                .select(Query.query(criteria), AuditRecordEntity.class)
                .count();
        var archived = entityOperations
                .select(Query.query(criteria), AuditArchiveEntity.class)
                .count();
        return Mono.zip(active, archived).map(tuple -> tuple.getT1() + tuple.getT2());
    }

    // 按天统计操作数量，委托给带用户筛选的重载。
    public Mono<Map<LocalDate, Long>> countPerDay(Instant since, String operation, String result) {
        return countPerDay(since, null, operation, result);
    }

    // 跨活跃与归档表按北京时间自然日聚合统计。
    public Mono<Map<LocalDate, Long>> countPerDay(
            Instant since, String uid, String operation, String result) {
        return countPerDay(since, null, uid, operation, result);
    }

    // 按可选的结束时刻限制统计区间。
    public Mono<Map<LocalDate, Long>> countPerDay(
            Instant since, Instant to, String uid, String operation, String result) {
        var sql = new StringBuilder("""
                        SELECT day, SUM(total) AS total FROM (
                            SELECT (occurred_at AT TIME ZONE 'Asia/Shanghai')::date AS day,
                                   COUNT(*) AS total
                            FROM audit_record
                            WHERE occurred_at >= :since
                        """);
        if (to != null) {
            sql.append(" AND occurred_at < :to");
        }
        sql.append(NON_STATIC_REQUEST_SQL);
        var archiveSql = new StringBuilder("""
                            UNION ALL
                            SELECT (occurred_at AT TIME ZONE 'Asia/Shanghai')::date AS day,
                                   COUNT(*) AS total
                            FROM audit_record_archive
                            WHERE occurred_at >= :since
                        """);
        if (to != null) {
            archiveSql.append(" AND occurred_at < :to");
        }
        archiveSql.append(NON_STATIC_REQUEST_SQL);
        if (StringUtils.hasText(uid)) {
            var condition = " AND uid = :uid";
            sql.append(condition);
            archiveSql.append(condition);
        }
        if (StringUtils.hasText(operation)) {
            var condition = " AND operation = :operation";
            sql.append(condition);
            archiveSql.append(condition);
        }
        if (StringUtils.hasText(result)) {
            var condition = " AND result = :result";
            sql.append(condition);
            archiveSql.append(condition);
        }
        sql.append(" GROUP BY 1");
        archiveSql.append(" GROUP BY 1");
        sql.append("\n").append(archiveSql).append(") grouped GROUP BY day ORDER BY day");
        var spec = databaseClient.sql(sql.toString()).bind("since", toOffsetDateTime(since));
        if (to != null) {
            spec = spec.bind("to", toOffsetDateTime(to));
        }
        if (StringUtils.hasText(uid)) {
            spec = spec.bind("uid", uid);
        }
        if (StringUtils.hasText(operation)) {
            spec = spec.bind("operation", operation);
        }
        if (StringUtils.hasText(result)) {
            spec = spec.bind("result", result);
        }
        return spec.map((row, metadata) -> {
                    var day = Objects.requireNonNull(row.get("day", LocalDate.class));
                    var total = row.get("total", Number.class);
                    return Map.entry(day, total == null ? 0L : total.longValue());
                })
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    // 按结果统计指定时间区间内的审计数量。
    public Mono<Map<String, Long>> countByResult(Instant from, Instant to, String operation) {
        var activeSql = new StringBuilder("""
                        SELECT result, SUM(total) AS total FROM (
                        SELECT result, COUNT(*) AS total
                        FROM audit_record
                        WHERE occurred_at >= :from
                          AND occurred_at < :to
                        """);
        activeSql.append(NON_STATIC_REQUEST_SQL);
        var archiveSql = new StringBuilder("""
                        UNION ALL
                        SELECT result, COUNT(*) AS total
                        FROM audit_record_archive
                        WHERE occurred_at >= :from
                          AND occurred_at < :to
                        """);
        archiveSql.append(NON_STATIC_REQUEST_SQL);
        if (StringUtils.hasText(operation)) {
            activeSql.append(" AND operation = :operation");
            archiveSql.append(" AND operation = :operation");
        }
        activeSql.append(" GROUP BY result");
        archiveSql.append(" GROUP BY result");
        activeSql
                .append("\n")
                .append(archiveSql)
                .append(") grouped GROUP BY result ORDER BY total DESC, result ASC");
        var spec = databaseClient
                .sql(activeSql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to));
        if (StringUtils.hasText(operation)) {
            spec = spec.bind("operation", operation);
        }
        return spec.map((row, metadata) -> Map.entry(
                        Objects.requireNonNull(row.get("result", String.class)),
                        Objects.requireNonNull(row.get("total", Number.class)).longValue()))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    // 统计区间内产生审计记录的去重用户数。
    public Mono<Long> countDistinctUids(Instant from, Instant to) {
        var sql = new StringBuilder("""
                        SELECT COUNT(DISTINCT uid) AS total FROM (
                            SELECT uid
                            FROM audit_record
                            WHERE occurred_at >= :from
                              AND occurred_at < :to
                              AND uid IS NOT NULL
                              AND uid <> ''
                              AND uid <> 'anonymous'
                        """);
        sql.append(NON_STATIC_REQUEST_SQL);
        var archiveSql = new StringBuilder("""
                            UNION ALL
                            SELECT uid
                            FROM audit_record_archive
                            WHERE occurred_at >= :from
                              AND occurred_at < :to
                              AND uid IS NOT NULL
                              AND uid <> ''
                              AND uid <> 'anonymous'
                        """);
        archiveSql.append(NON_STATIC_REQUEST_SQL);
        sql.append("\n").append(archiveSql).append(") active_users");
        return databaseClient
                .sql(sql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> Objects.requireNonNull(row.get("total", Number.class)).longValue())
                .one()
                .defaultIfEmpty(0L);
    }

    // 按操作类型统计区间内的高频操作。
    public Mono<Map<String, Long>> topOperations(Instant from, Instant to, int limit) {
        var activeSql = new StringBuilder("""
                        SELECT operation, SUM(total) AS total FROM (
                        SELECT operation, COUNT(*) AS total
                        FROM audit_record
                        WHERE occurred_at >= :from
                          AND occurred_at < :to
                        """);
        activeSql.append(NON_STATIC_REQUEST_SQL);
        var archiveSql = new StringBuilder("""
                        UNION ALL
                        SELECT operation, COUNT(*) AS total
                        FROM audit_record_archive
                        WHERE occurred_at >= :from
                          AND occurred_at < :to
                        """);
        archiveSql.append(NON_STATIC_REQUEST_SQL);
        activeSql.append(" GROUP BY operation");
        archiveSql.append(" GROUP BY operation");
        activeSql
                .append("\n")
                .append(archiveSql)
                .append(") grouped GROUP BY operation ORDER BY total DESC, operation ASC LIMIT :limit");
        return databaseClient
                .sql(activeSql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .bind("limit", Math.max(1, limit))
                .map((row, metadata) -> Map.entry(
                        Objects.requireNonNull(row.get("operation", String.class)),
                        Objects.requireNonNull(row.get("total", Number.class)).longValue()))
                .all()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    // 汇总区间内审计请求的平均与 P95 耗时。
    public Mono<LatencyStats> latencyStats(Instant from, Instant to) {
        var sql = new StringBuilder("WITH records AS (")
                .append(recordsCte())
                .append("""
                        )
                        SELECT COALESCE(ROUND(AVG(duration_ms)), 0) AS average_ms,
                               COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0) AS p95_ms
                        FROM records
                        """);
        return databaseClient
                .sql(sql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> new LatencyStats(
                        longValue(row.get("average_ms")),
                        longValue(row.get("p95_ms"))))
                .one()
                .defaultIfEmpty(new LatencyStats(0L, 0L));
    }

    // 按北京时间星期与小时汇总审计活动。
    public Flux<HeatmapBucket> activityHeatmap(Instant from, Instant to) {
        var sql = new StringBuilder("WITH records AS (")
                .append(recordsCte())
                .append("""
                        )
                        SELECT EXTRACT(ISODOW FROM occurred_at AT TIME ZONE 'Asia/Shanghai')::int AS weekday,
                               EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Shanghai')::int AS hour,
                               COUNT(*) AS total
                        FROM records
                        GROUP BY 1, 2
                        ORDER BY 1, 2
                        """);
        return databaseClient
                .sql(sql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> new HeatmapBucket(
                        numberValue(row.get("weekday")),
                        numberValue(row.get("hour")),
                        longValue(row.get("total"))))
                .all();
    }

    // 按固定区间统计区间内审计请求的耗时分布。
    public Mono<Map<String, Long>> durationBuckets(Instant from, Instant to) {
        var sql = new StringBuilder("WITH records AS (")
                .append(recordsCte())
                .append("""
                        )
                        SELECT CASE
                                   WHEN duration_ms < 50 THEN 'under50'
                                   WHEN duration_ms < 200 THEN '50to199'
                                   WHEN duration_ms < 500 THEN '200to499'
                                   WHEN duration_ms < 1000 THEN '500to999'
                                   WHEN duration_ms < 3000 THEN '1to3s'
                                   ELSE 'over3s'
                               END AS bucket,
                               COUNT(*) AS total
                        FROM records
                        GROUP BY bucket
                        """);
        return bucketMap(databaseClient
                .sql(sql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> Map.entry(
                        Objects.requireNonNull(row.get("bucket", String.class)),
                        longValue(row.get("total"))))
                .all());
    }

    // 按用户汇总区间内审计活跃度并排除匿名身份。
    public Flux<ActorStat> topActors(Instant from, Instant to, int limit) {
        var sql = new StringBuilder("WITH records AS (")
                .append(recordsCte())
                .append("""
                        )
                        SELECT uid,
                               COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE result <> 'SUCCESS') AS failures,
                               COUNT(DISTINCT operation) AS operations,
                               COALESCE(ROUND(AVG(duration_ms)), 0) AS average_ms
                        FROM records
                        WHERE uid IS NOT NULL
                          AND uid <> ''
                          AND LOWER(uid) NOT IN ('anonymous', 'unknown')
                        GROUP BY uid
                        ORDER BY total DESC, uid ASC
                        LIMIT :limit
                        """);
        return databaseClient
                .sql(sql.toString())
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .bind("limit", Math.max(1, limit))
                .map((row, metadata) -> new ActorStat(
                        Objects.requireNonNull(row.get("uid", String.class)),
                        longValue(row.get("total")),
                        longValue(row.get("failures")),
                        longValue(row.get("operations")),
                        longValue(row.get("average_ms"))))
                .all();
    }

    // 汇总区间内追踪流的数量、状态、归档与耗时。
    public Mono<FlowStats> flowStats(Instant from, Instant to) {
        var sql = "WITH " + flowCte() + """
                SELECT (SELECT COUNT(*) FROM records) AS records,
                       COUNT(*) AS flows,
                       COUNT(*) FILTER (WHERE failed) AS failed_flows,
                       COUNT(*) FILTER (WHERE archived) AS archived_flows,
                       COALESCE(AVG(step_count), 0) AS average_steps,
                       COALESCE(ROUND(AVG(duration_ms)), 0) AS average_duration_ms,
                       COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0) AS p95_duration_ms
                FROM flow_durations
                """;
        return databaseClient
                .sql(sql)
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> new FlowStats(
                        longValue(row.get("records")),
                        longValue(row.get("flows")),
                        longValue(row.get("failed_flows")),
                        longValue(row.get("archived_flows")),
                        doubleValue(row.get("average_steps")),
                        longValue(row.get("average_duration_ms")),
                        longValue(row.get("p95_duration_ms"))))
                .one()
                .defaultIfEmpty(new FlowStats(0L, 0L, 0L, 0L, 0, 0L, 0L));
    }

    // 按北京时间追踪流开始日期汇总每日流量。
    public Flux<FlowDayBucket> dailyFlowBuckets(Instant from, Instant to) {
        var sql = "WITH " + flowCte() + """
                SELECT (started_at AT TIME ZONE 'Asia/Shanghai')::date AS day,
                       COUNT(*) AS flows,
                       SUM(step_count) AS records,
                       COUNT(*) FILTER (WHERE failed) AS failed_flows,
                       SUM(archived_step_count) AS archived_records
                FROM flows
                GROUP BY 1
                ORDER BY 1
                """;
        return databaseClient
                .sql(sql)
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> new FlowDayBucket(
                        Objects.requireNonNull(row.get("day", LocalDate.class)),
                        longValue(row.get("flows")),
                        longValue(row.get("records")),
                        longValue(row.get("failed_flows")),
                        longValue(row.get("archived_records"))))
                .all();
    }

    // 按追踪流开始时间汇总北京时间星期与小时活动。
    public Flux<HeatmapBucket> flowHeatmap(Instant from, Instant to) {
        var sql = "WITH " + flowCte() + """
                SELECT EXTRACT(ISODOW FROM started_at AT TIME ZONE 'Asia/Shanghai')::int AS weekday,
                       EXTRACT(HOUR FROM started_at AT TIME ZONE 'Asia/Shanghai')::int AS hour,
                       COUNT(*) AS total
                FROM flows
                GROUP BY 1, 2
                ORDER BY 1, 2
                """;
        return databaseClient
                .sql(sql)
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> new HeatmapBucket(
                        numberValue(row.get("weekday")),
                        numberValue(row.get("hour")),
                        longValue(row.get("total"))))
                .all();
    }

    // 按固定步骤区间统计追踪流数量。
    public Mono<Map<String, Long>> flowStepBuckets(Instant from, Instant to) {
        var sql = "WITH " + flowCte() + """
                SELECT CASE
                           WHEN step_count = 1 THEN '1'
                           WHEN step_count = 2 THEN '2'
                           WHEN step_count = 3 THEN '3'
                           WHEN step_count <= 5 THEN '4to5'
                           WHEN step_count <= 10 THEN '6to10'
                           ELSE 'over10'
                       END AS bucket,
                       COUNT(*) AS total
                FROM flows
                GROUP BY bucket
                """;
        return bucketMap(databaseClient
                .sql(sql)
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> Map.entry(
                        Objects.requireNonNull(row.get("bucket", String.class)),
                        longValue(row.get("total"))))
                .all());
    }

    // 按固定耗时区间统计追踪流数量。
    public Mono<Map<String, Long>> flowDurationBuckets(Instant from, Instant to) {
        var sql = "WITH " + flowCte() + """
                SELECT CASE
                           WHEN duration_ms < 50 THEN 'under50'
                           WHEN duration_ms < 200 THEN '50to199'
                           WHEN duration_ms < 500 THEN '200to499'
                           WHEN duration_ms < 1000 THEN '500to999'
                           WHEN duration_ms < 3000 THEN '1to3s'
                           ELSE 'over3s'
                       END AS bucket,
                       COUNT(*) AS total
                FROM flow_durations
                GROUP BY bucket
                """;
        return bucketMap(databaseClient
                .sql(sql)
                .bind("from", toOffsetDateTime(from))
                .bind("to", toOffsetDateTime(to))
                .map((row, metadata) -> Map.entry(
                        Objects.requireNonNull(row.get("bucket", String.class)),
                        longValue(row.get("total"))))
                .all());
    }

    // 按时间倒序返回最近的审计记录。
    public Flux<AuditRecord> findRecent(int limit) {
        var pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "id"));
        return selectBoth(nonStaticRequests(), pageable)
                .sort(recordOrder(false))
                .take(Math.max(1, limit));
    }

    // 按步骤顺序返回同一追踪流的全部记录。
    public Flux<AuditRecord> findByTraceFlowId(String traceFlowId) {
        if (!StringUtils.hasText(traceFlowId)) {
            return Flux.empty();
        }
        var pageable = PageRequest.of(
                0, MAX_CHAIN_SIZE, Sort.by(Sort.Direction.ASC, "stepIndex").and(Sort.by("occurredAt")));
        return selectBoth(Criteria.where("traceFlowId").is(traceFlowId), pageable)
                .sort(Comparator.comparing(
                                (AuditRecord record) ->
                                        record.getStepIndex() == null ? Integer.MAX_VALUE : record.getStepIndex())
                        .thenComparing(record -> record.getTimestamp())
                        .thenComparing(record -> record.getMetadata().getName()));
    }

    // 返回指定用户时间区间内的审计记录。
    public Flux<AuditRecord> findByUidBetween(String uid, Instant from, Instant to) {
        if (!StringUtils.hasText(uid) || from == null || to == null) {
            return Flux.empty();
        }
        var criteria = Criteria.where("uid")
                .is(uid)
                .and("occurredAt")
                .greaterThanOrEquals(toOffsetDateTime(from))
                .and("occurredAt")
                .lessThan(toOffsetDateTime(to))
                .and(nonStaticRequests());
        var pageable = PageRequest.of(
                0, MAX_GRAPH_SIZE, Sort.by(Sort.Direction.ASC, "occurredAt").and(Sort.by("id")));
        return selectBoth(criteria, pageable)
                .sort(Comparator.comparing((AuditRecord record) -> record.getTimestamp())
                        .thenComparing(record -> record.getMetadata().getName()));
    }

    // 同时查询活跃与归档表并去重合并。
    private Flux<AuditRecord> selectBoth(Criteria criteria, PageRequest pageable) {
        var dataQuery = Query.query(criteria).with(pageable);
        var active = entityOperations
                .select(dataQuery, AuditRecordEntity.class)
                .map(entity -> entity.toRecord(false));
        var archived = entityOperations
                .select(dataQuery, AuditArchiveEntity.class)
                .map(AuditArchiveEntity::toRecord);
        return Flux.merge(active, archived).distinct(record -> record.getMetadata().getName());
    }

    // 抢占归档锁后分批迁移记录，结束时释放锁。
    public Mono<Long> archiveBefore(Instant cutoff) {
        return acquireArchiveGuard().flatMap(acquired -> {
            if (!acquired) {
                return Mono.just(0L);
            }
            return archiveBatch(toOffsetDateTime(cutoff), 0L)
                    .flatMap(count -> releaseArchiveGuard().thenReturn(count))
                    .onErrorResume(error -> releaseArchiveGuard().then(Mono.error(error)));
        });
    }

    // 条件更新抢占归档锁，多实例同时归档时只有抢到的实例继续执行
    private Mono<Boolean> acquireArchiveGuard() {
        return databaseClient
                .sql("UPDATE audit_archive_guard SET enabled = TRUE WHERE id = 1 AND enabled = FALSE")
                .fetch()
                .rowsUpdated()
                .map(count -> count > 0);
    }

    // 释放归档锁，允许其他实例继续归档。
    private Mono<Void> releaseArchiveGuard() {
        return guardRepository.save(new AuditArchiveGuardEntity(1, false)).then();
    }

    // 分批迁移并删除活跃记录，单批使用事务保证一致。
    private Mono<Long> archiveBatch(OffsetDateTime cutoff, long archivedCount) {
        var batchQuery = Query.query(Criteria.where("occurredAt").lessThan(cutoff))
                .with(PageRequest.of(0, ARCHIVE_BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
        return entityOperations
                .select(batchQuery, AuditRecordEntity.class)
                .collectList()
                .flatMap(batch -> {
                    if (batch.isEmpty()) {
                        return Mono.just(archivedCount);
                    }
                    var archiveEntities = batch.stream().map(AuditArchiveEntity::from).toList();
                    return Flux.fromIterable(archiveEntities)
                            .concatMap(entityOperations::insert)
                            .then(activeRepository.deleteAll(batch))
                            .as(transactionalOperator::transactional)
                            .then(Mono.defer(() -> archiveBatch(cutoff, archivedCount + batch.size())));
                });
    }

    // 统一以协调世界时偏移量写入数据库时间字段。
    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    // 构建跨活跃与归档表的统一审计记录 CTE。
    private static String recordsCte() {
        return """
                SELECT operation, result, duration_ms, uid, occurred_at, trace_flow_id, FALSE AS archived
                FROM audit_record
                WHERE occurred_at >= :from
                  AND occurred_at < :to
                """
                + NON_STATIC_REQUEST_SQL
                + """
                UNION ALL
                SELECT operation, result, duration_ms, uid, occurred_at, trace_flow_id, TRUE AS archived
                FROM audit_record_archive
                WHERE occurred_at >= :from
                  AND occurred_at < :to
                """
                + NON_STATIC_REQUEST_SQL;
    }

    // 构建追踪流及其起止时间、状态与耗时的统一 CTE。
    private static String flowCte() {
        return "records AS ("
                + recordsCte()
                + """
                ),
                flows AS (
                    SELECT trace_flow_id,
                           MIN(occurred_at - GREATEST(duration_ms, 0) * INTERVAL '1 millisecond') AS started_at,
                           MAX(occurred_at) AS ended_at,
                           COUNT(*) AS step_count,
                           COUNT(*) FILTER (WHERE archived) AS archived_step_count,
                           BOOL_OR(result IN ('DENIED', 'FAILURE', 'FAILED')) AS failed,
                           BOOL_OR(archived) AS archived
                    FROM records
                    WHERE trace_flow_id IS NOT NULL
                      AND BTRIM(trace_flow_id) <> ''
                    GROUP BY trace_flow_id
                ),
                flow_durations AS (
                    SELECT trace_flow_id,
                           started_at,
                           ended_at,
                           step_count,
                           archived_step_count,
                           failed,
                           archived,
                           GREATEST(
                               0,
                               ROUND(EXTRACT(EPOCH FROM (ended_at - started_at)) * 1000)::bigint
                           ) AS duration_ms
                    FROM flows
                )
                """;
    }

    // 将数据库数值转换为非负长整型。
    private static long longValue(Object value) {
        return value instanceof Number number ? Math.max(0L, Math.round(number.doubleValue())) : 0L;
    }

    // 将数据库数值转换为非负浮点数。
    private static double doubleValue(Object value) {
        return value instanceof Number number ? Math.max(0, number.doubleValue()) : 0;
    }

    // 将分桶行合并为有序映射。
    private static Mono<Map<String, Long>> bucketMap(Flux<Map.Entry<String, Long>> rows) {
        return rows.collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new));
    }

    // 将数据库数值转换为整型。
    private static int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public record AuditQuery(
            String cursor,
            Instant from,
            Instant to,
            String uid,
            String operation,
            String result,
            String traceId,
            String traceFlowId,
            int limit,
            boolean ascending) {

        // 创建升序游标查询，供图表按时间正序读取。
        public static AuditQuery ascending(
                String cursor,
                Instant from,
                Instant to,
                String uid,
                String operation,
                String result,
                String traceId,
                String traceFlowId,
                int limit) {
            return new AuditQuery(
                    cursor, from, to, uid, operation, result, traceId, traceFlowId, limit, true);
        }
    }

    public record AuditPageResult(List<AuditRecord> items, long total) {}

    public record ArchiveStats(long activeCount, long archivedCount) {}

    public record LatencyStats(long averageMs, long p95Ms) {}

    public record HeatmapBucket(int weekday, int hour, long records) {}

    public record ActorStat(String uid, long records, long failures, long operations, long averageMs) {}

    public record FlowStats(
            long records,
            long flows,
            long failedFlows,
            long archivedFlows,
            double averageSteps,
            long averageDurationMs,
            long p95DurationMs) {}

    public record FlowDayBucket(
            LocalDate day, long flows, long records, long failedFlows, long archivedRecords) {}

    public record TraceFlowPage(List<String> traceFlowIds, long total, boolean truncated) {}
}
