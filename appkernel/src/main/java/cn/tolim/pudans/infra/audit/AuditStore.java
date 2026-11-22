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
                     AND request_path <> '/favicon.ico'))
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
        var sql = new StringBuilder("""
                        SELECT day, SUM(total) AS total FROM (
                            SELECT (occurred_at AT TIME ZONE 'Asia/Shanghai')::date AS day,
                                   COUNT(*) AS total
                            FROM audit_record
                            WHERE occurred_at >= :since
                              AND (request_path IS NULL
                                   OR (request_path NOT LIKE '/assets/%'
                                       AND request_path NOT LIKE '/brand/%'
                                       AND request_path NOT LIKE '/webjars/%'
                                       AND request_path <> '/favicon.ico'))
                        """);
        var archiveSql = new StringBuilder("""
                            UNION ALL
                            SELECT (occurred_at AT TIME ZONE 'Asia/Shanghai')::date AS day,
                                   COUNT(*) AS total
                            FROM audit_record_archive
                            WHERE occurred_at >= :since
                              AND (request_path IS NULL
                                   OR (request_path NOT LIKE '/assets/%'
                                       AND request_path NOT LIKE '/brand/%'
                                       AND request_path NOT LIKE '/webjars/%'
                                       AND request_path <> '/favicon.ico'))
                        """);
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

    public record TraceFlowPage(List<String> traceFlowIds, long total, boolean truncated) {}
}
