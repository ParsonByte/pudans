package cn.tolim.pudans.infra.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;

@Slf4j
@Component
public class AuditStore {

    private static final int ARCHIVE_BATCH_SIZE = 500;

    private static final int MAX_CHAIN_SIZE = 2_000;

    private static final int MAX_GRAPH_SIZE = 10_000;

    private static final int MAX_TRACE_GROUP_RECORDS = 20_000;

    private final AuditRecordRepository activeRepository;

    private final AuditArchiveRepository archiveRepository;

    private final AuditArchiveGuardRepository guardRepository;

    private final R2dbcEntityOperations entityOperations;

    private final TransactionalOperator transactionalOperator;

    public AuditStore(
            AuditRecordRepository activeRepository,
            AuditArchiveRepository archiveRepository,
            AuditArchiveGuardRepository guardRepository,
            R2dbcEntityOperations entityOperations,
            ReactiveTransactionManager reactiveTransactionManager) {
        this.activeRepository = activeRepository;
        this.archiveRepository = archiveRepository;
        this.guardRepository = guardRepository;
        this.entityOperations = entityOperations;
        this.transactionalOperator = TransactionalOperator.create(reactiveTransactionManager);
    }

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

    public Mono<AuditPageResult> page(AuditQuery query, int page, int size) {
        var direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        var fetchLimit = (int) Math.min(Integer.MAX_VALUE, (long) page * size);
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

    public Mono<ArchiveStats> archiveStats() {
        var query = Query.query(nonStaticRequests());
        return Mono.zip(
                        entityOperations.select(query, AuditRecordEntity.class).count(),
                        entityOperations.select(query, AuditArchiveEntity.class).count())
                .map(tuple -> new ArchiveStats(tuple.getT1(), tuple.getT2()));
    }

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

    private static Comparator<AuditRecord> recordOrder(boolean ascending) {
        Comparator<AuditRecord> order = Comparator.comparing(record -> record.getMetadata().getName());
        return ascending ? order : order.reversed();
    }

    private static Criteria criteria(AuditQuery query) {
        var criteria = nonStaticRequests();
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

    public Mono<AuditRecord> findById(String auditId) {
        return activeRepository
                .findById(auditId)
                .map(entity -> entity.toRecord(false))
                .switchIfEmpty(archiveRepository.findById(auditId).map(AuditArchiveEntity::toRecord));
    }

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

    public Mono<Map<LocalDate, Long>> countPerDay(Instant since, String operation, String result) {
        return countPerDay(since, null, operation, result);
    }

    public Mono<Map<LocalDate, Long>> countPerDay(
            Instant since, String uid, String operation, String result) {
        var criteria = Criteria.where("occurredAt")
                .greaterThanOrEquals(toOffsetDateTime(since))
                .and(nonStaticRequests());
        if (StringUtils.hasText(uid)) {
            criteria = criteria.and("uid").is(uid);
        }
        if (StringUtils.hasText(operation)) {
            criteria = criteria.and("operation").is(operation);
        }
        if (StringUtils.hasText(result)) {
            criteria = criteria.and("result").is(result);
        }
        var pageable = PageRequest.of(0, MAX_GRAPH_SIZE, Sort.by(Sort.Direction.ASC, "id"));
        var query = Query.query(criteria);
        var active = entityOperations
                .select(AuditRecordEntity.class)
                .matching(query.with(pageable))
                .all()
                .map(entity -> entity.toRecord(false));
        var archived = entityOperations
                .select(AuditArchiveEntity.class)
                .matching(query.with(pageable))
                .all()
                .map(AuditArchiveEntity::toRecord);
        return Flux.merge(active, archived)
                .map(record -> record.getTimestamp() == null
                        ? null
                        : LocalDate.ofInstant(record.getTimestamp(), ZoneOffset.UTC))
                .filter(Objects::nonNull)
                .collectList()
                .map(days -> days.stream()
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
    }

    public Flux<AuditRecord> findRecent(int limit) {
        var pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "id"));
        return selectBoth(nonStaticRequests(), pageable)
                .sort(recordOrder(false))
                .take(Math.max(1, limit));
    }

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

    public Mono<Long> archiveBefore(Instant cutoff) {
        return setGuard(true)
                .then(Mono.defer(() -> archiveBatch(toOffsetDateTime(cutoff), 0L)))
                .flatMap(count -> setGuard(false).thenReturn(count))
                .onErrorResume(error -> setGuard(false).then(Mono.error(error)));
    }

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

    private Mono<Void> setGuard(boolean enabled) {
        return guardRepository.save(new AuditArchiveGuardEntity(1, enabled)).then();
    }

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
