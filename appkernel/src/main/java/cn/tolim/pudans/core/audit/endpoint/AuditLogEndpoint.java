package cn.tolim.pudans.core.audit.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.infra.audit.AuditStore;
import cn.tolim.pudans.core.infra.utils.PudansTime;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class AuditLogEndpoint implements CustomEndpoint {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_JOURNEY_RECORDS = 20_000;
    private static final long JOURNEY_SESSION_GAP_MS = Duration.ofMinutes(30).toMillis();
    private static final long PAGE_DUPLICATE_WINDOW_MS = 10_000;
    private static final String DEFAULT_ORDER = "desc";

    private final AuditLog auditLog;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "AuditLogV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/auditlogs", this::list, builder -> {
                    builder.operationId("ListAuditLogs")
                            .description("Query active and archived audit records using an audit id cursor.")
                            .tag(tag)
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("cursor")
                                    .description(
                                            "Audit id cursor. With the default desc order, records with an id "
                                                    + "smaller than the cursor are returned; with order=asc, records "
                                                    + "with an id greater than the cursor are returned.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("order")
                                    .description("Sort direction by audit id: desc (default) or asc.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("limit")
                                    .description("Maximum number of records (1-500, default 50).")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("page")
                                    .description("Page number when page-based pagination is used.")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("size")
                                    .description("Page size when page-based pagination is used (1-100, default 10).")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("uid")
                                    .description("Filter by user uid.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("operation")
                                    .description("Filter by operation.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("result")
                                    .description("Filter by result (SUCCESS, FAILURE, DENIED).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("traceId")
                                    .description("Filter by request trace id.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("traceFlowId")
                                    .description("Filter by business trace flow id.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("from")
                                    .description("Inclusive lower bound of the UTC timestamp (ISO-8601).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("to")
                                    .description("Exclusive upper bound of the UTC timestamp (ISO-8601).")
                                    .implementation(String.class))
                            .response(responseBuilder().implementation(AuditLogPage.class));
                })
                .GET("/auditlogs/traces", this::traces, builder -> {
                    builder.operationId("ListAuditTraceFlows")
                            .description(
                                    "Query audit trace flows grouped by traceFlowId and return each flow with all "
                                            + "of its audit records.")
                            .tag(tag)
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("page")
                                    .description("Page number (default 1).")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("size")
                                    .description("Page size (1-100, default 10).")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("uid")
                                    .description("Filter by user uid.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("operation")
                                    .description("Filter by operation.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("result")
                                    .description("Filter by result (SUCCESS, FAILURE, DENIED).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("traceId")
                                    .description("Filter by request trace id.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("traceFlowId")
                                    .description("Filter by business trace flow id.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("from")
                                    .description("Inclusive lower bound of the UTC timestamp (ISO-8601).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("to")
                                    .description("Exclusive upper bound of the UTC timestamp (ISO-8601).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("summary")
                                    .description("Return trace flow summaries without records when true.")
                                    .implementation(Boolean.class))
                            .response(responseBuilder().implementation(AuditTracePage.class));
                })
                .GET("/auditlogs/journeys", this::journeys, builder -> {
                    builder.operationId("ListAuditJourneys")
                            .description(
                                    "Return one closed user journey per user. A journey links the pages a user "
                                            + "visited with the operations performed on each page.")
                            .tag(tag)
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("page")
                                    .description("Page number (default 1).")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("size")
                                    .description("Page size (1-100, default 10).")
                                    .implementation(Integer.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("uid")
                                    .description("Filter by user uid.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("from")
                                    .description("Inclusive lower bound of the UTC timestamp (ISO-8601).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("to")
                                    .description("Exclusive upper bound of the UTC timestamp (ISO-8601).")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("summary")
                                    .description("Return journey summaries without steps when true.")
                                    .implementation(Boolean.class))
                            .response(responseBuilder().implementation(AuditJourneyPage.class));
                })
                .GET("/auditlogs/archive-stats", this::archiveStats, builder -> builder.operationId("GetAuditArchiveStats")
                        .description("Return active and archived audit record counts.")
                        .tag(tag)
                        .response(responseBuilder().implementation(AuditArchiveStats.class)))
                .GET("/auditlogs/archives", this::archives, builder -> builder.operationId("ListArchivedAuditLogs")
                        .description("Query archived audit records with page-based pagination.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("page")
                                .description("Page number (default 1).")
                                .implementation(Integer.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("size")
                                .description("Page size (1-100, default 10).")
                                .implementation(Integer.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("uid")
                                .description("Filter by user uid.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("operation")
                                .description("Filter by operation.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("result")
                                .description("Filter by result.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("traceId")
                                .description("Filter by trace id.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("traceFlowId")
                                .description("Filter by trace flow id.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("from")
                                .description("Inclusive lower bound of the UTC timestamp (ISO-8601).")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("to")
                                .description("Exclusive upper bound of the UTC timestamp (ISO-8601).")
                                .implementation(String.class))
                        .response(responseBuilder().implementation(AuditLogPage.class)))
                .POST("/auditlogs/archive", this::archive, builder -> builder.operationId("ArchiveAuditLogs")
                        .description("Move audit records older than the requested cutoff into the archive.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .implementation(AuditArchiveRequest.class))
                        .response(responseBuilder().implementation(AuditArchiveResult.class)))
                .GET("/auditlogs/graph", this::graph, builder -> builder.operationId("GetDailyAuditGraph")
                        .description(
                                "Daily user operation graph: nodes are audit steps and edges are parent/child "
                                        + "relations.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("uid")
                                .description("User uid (required).")
                                .required(true)
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("date")
                                .description("UTC date in ISO-8601 format, e.g. 2026-11-14 (required).")
                                .required(true)
                                .implementation(String.class))
                        .response(responseBuilder().implementation(AuditGraph.class)))
                .GET("/auditlogs/{auditId}/trace", this::trace, builder -> builder.operationId("GetAuditTrace")
                        .description(
                                "Return the complete trace flow containing the audit record: previous and following "
                                        + "steps.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.PATH)
                                .name("auditId")
                                .required(true)
                                .implementation(String.class))
                        .response(responseBuilder().implementation(AuditTrace.class)))
                .GET("/auditlogs/replay/{traceFlowId}", this::replay, builder -> builder.operationId("ReplayAuditFlow")
                        .description("Virtual read-only replay of stored actionSnapshot values for a trace flow.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.PATH)
                                .name("traceFlowId")
                                .required(true)
                                .implementation(String.class))
                        .response(responseBuilder().implementation(AuditReplay.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        if (request.queryParam("page").filter(StringUtils::hasText).isPresent()) {
            return page(request);
        }
        var limit = intParam(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        var ascending = isAscending(request);
        var query = auditQuery(request, request.queryParam("cursor").orElse(null), limit + 1, ascending);
        return auditLog
                .query(query)
                .take(limit + 1L)
                .collectList()
                .map(records -> {
                    var hasMore = records.size() > limit;
                    var items = hasMore ? records.subList(0, limit) : records;
                    var nextCursor = items.isEmpty()
                            ? null
                            : items.get(items.size() - 1).getMetadata().getName();
                    return new AuditLogPage(items, nextCursor, hasMore, null, null, null);
                })
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> page(ServerRequest request) {
        var page = intParam(request, "page", 1, 1, Integer.MAX_VALUE);
        var size = intParam(request, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        if ((long) page * size > AuditStore.MAX_PAGE_RECORDS) {
            return Mono.error(new ServerWebInputException(
                    "分页深度超出上限，page 与 size 的乘积不能超过 " + AuditStore.MAX_PAGE_RECORDS + "。"));
        }
        var query = auditQuery(request, null, size, isAscending(request));
        return auditLog
                .page(query, page, size)
                .map(result -> {
                    var items = result.items();
                    var hasMore = (long) page * size < result.total();
                    var nextCursor = items.isEmpty()
                            ? null
                            : items.get(items.size() - 1).getMetadata().getName();
                    return new AuditLogPage(
                            items, nextCursor, hasMore, result.total(), page, size);
                })
                .flatMap(pageResult -> ServerResponse.ok().bodyValue(pageResult));
    }

    private Mono<ServerResponse> traces(ServerRequest request) {
        var page = intParam(request, "page", 1, 1, Integer.MAX_VALUE);
        var size = intParam(request, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        var summary = Boolean.parseBoolean(request.queryParam("summary").orElse("false"));
        var query = auditQuery(request, null, size, isAscending(request));
        return auditLog
                .traceFlows(query, page, size)
                .flatMap(result -> Flux.fromIterable(result.traceFlowIds())
                        .concatMap(traceFlowId ->
                                auditLog.findByTraceFlowId(traceFlowId).collectList())
                        .map(records -> toTraceSummary(records, !summary))
                        .collectList()
                        .map(items -> new AuditTracePage(
                                items, result.total(), page, size, result.truncated())))
                .flatMap(pageResult -> ServerResponse.ok().bodyValue(pageResult));
    }

    private Mono<ServerResponse> journeys(ServerRequest request) {
        var page = intParam(request, "page", 1, 1, Integer.MAX_VALUE);
        var size = intParam(request, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        var summary = Boolean.parseBoolean(request.queryParam("summary").orElse("false"));
        var scan = new JourneyScan((int) Math.min(MAX_JOURNEY_RECORDS, (long) page * size));
        return auditLog
                .query(auditQuery(request, null, MAX_JOURNEY_RECORDS + 1, false))
                .filter(AuditLogEndpoint::isJourneyUid)
                .reduce(scan, (current, record) -> current.add(record))
                .flatMap(current -> !summary && current.needsDetailRefill(page, size)
                        ? refillJourneyDetails(request, current, page, size)
                        : Mono.just(current.toPage(page, size, !summary)))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<AuditJourneyPage> refillJourneyDetails(
            ServerRequest request, JourneyScan scan, int page, int size) {
        // 首页条目缺少明细时补查一次，保证返回的足迹仍带完整步骤。
        var uids = scan.missingDetailUids(page, size);
        var limits = scan.recordCounts(uids);
        return auditLog
                .query(auditQuery(request, null, MAX_JOURNEY_RECORDS + 1, false))
                .filter(record -> uids.contains(record.getUid()))
                .take(MAX_JOURNEY_RECORDS)
                .collect(Collectors.groupingBy(AuditRecord::getUid))
                .doOnNext(grouped -> scan.attachDetails(grouped, limits))
                .then(Mono.fromSupplier(() -> scan.toPage(page, size, true)));
    }

    private static boolean isJourneyUid(AuditRecord record) {
        return StringUtils.hasText(record.getUid())
                && !"anonymous".equalsIgnoreCase(record.getUid())
                && !"unknown".equalsIgnoreCase(record.getUid());
    }

    private static final class JourneyScan {

        private final int candidateLimit;
        // 只为首页窗口内的用户保留明细记录，其余用户仅累计计数。
        private final Map<String, UidAggregate> aggregates = new LinkedHashMap<>();
        private int accepted;
        private boolean truncated;

        private JourneyScan(int candidateLimit) {
            this.candidateLimit = candidateLimit;
        }

        private JourneyScan add(AuditRecord record) {
            if (accepted >= MAX_JOURNEY_RECORDS) {
                truncated = true;
                return this;
            }
            accepted++;
            var aggregate = aggregates.get(record.getUid());
            if (aggregate == null) {
                aggregate = new UidAggregate(aggregates.size() < candidateLimit);
                aggregates.put(record.getUid(), aggregate);
            }
            aggregate.accept(record);
            return this;
        }

        private boolean needsDetailRefill(int page, int size) {
            return pageAggregates(page, size).stream()
                    .anyMatch(entry -> !entry.getValue().hasDetails());
        }

        private List<String> missingDetailUids(int page, int size) {
            return pageAggregates(page, size).stream()
                    .filter(entry -> !entry.getValue().hasDetails())
                    .map(Map.Entry::getKey)
                    .toList();
        }

        private Map<String, Long> recordCounts(List<String> uids) {
            return uids.stream()
                    .collect(Collectors.toMap(uid -> uid, uid -> aggregates.get(uid).recordCount()));
        }

        private void attachDetails(Map<String, List<AuditRecord>> grouped, Map<String, Long> limits) {
            for (var entry : grouped.entrySet()) {
                var aggregate = aggregates.get(entry.getKey());
                if (aggregate != null) {
                    aggregate.attach(entry.getValue(), limits.getOrDefault(entry.getKey(), 0L));
                }
            }
        }

        private AuditJourneyPage toPage(int page, int size, boolean includeSteps) {
            var items = pageAggregates(page, size).stream()
                    .map(entry -> {
                        var journey = toJourney(entry.getKey(), entry.getValue().details());
                        return includeSteps ? journey : journey.withoutSteps();
                    })
                    .toList();
            var pageViews = aggregates.values().stream().mapToLong(UidAggregate::pageCount).sum();
            var actionCount = aggregates.values().stream().mapToLong(UidAggregate::actionCount).sum();
            return new AuditJourneyPage(
                    items, aggregates.size(), page, size, truncated, pageViews, actionCount);
        }

        private List<Map.Entry<String, UidAggregate>> pageAggregates(int page, int size) {
            var ordered = aggregates.entrySet().stream()
                    .sorted(Comparator.comparing(
                                    (Map.Entry<String, UidAggregate> entry) -> entry.getValue().end())
                            .reversed())
                    .toList();
            var from = (int) Math.min(ordered.size(), (long) (page - 1) * size);
            var to = Math.min(ordered.size(), from + size);
            return ordered.subList(from, to);
        }
    }

    private static final class UidAggregate {

        private final List<PageMark> pages = new ArrayList<>();
        private List<AuditRecord> details;
        private long recordCount;
        private long actionCount;
        private Instant end = Instant.EPOCH;

        private UidAggregate(boolean retainDetails) {
            this.details = retainDetails ? new ArrayList<>() : null;
        }

        private void accept(AuditRecord record) {
            recordCount++;
            var timestamp = record.getTimestamp() == null ? Instant.EPOCH : record.getTimestamp();
            if (timestamp.isAfter(end)) {
                end = timestamp;
            }
            if (isPage(record)) {
                pages.add(new PageMark(
                        pagePath(record),
                        traceStart(record),
                        record.getStepIndex(),
                        record.getMetadata().getName()));
            } else {
                actionCount++;
            }
            if (details != null) {
                details.add(record);
            }
        }

        private void attach(List<AuditRecord> records, long limit) {
            details = records.stream()
                    .sorted(Comparator.comparing(
                                    (AuditRecord record) -> record.getMetadata().getName())
                            .reversed())
                    .limit(Math.max(0L, limit))
                    .toList();
        }

        private boolean hasDetails() {
            return details != null;
        }

        private List<AuditRecord> details() {
            return details == null ? List.of() : details;
        }

        private long recordCount() {
            return recordCount;
        }

        private long actionCount() {
            return actionCount;
        }

        private Instant end() {
            return end;
        }

        private long pageCount() {
            var ordered = pages.stream().sorted(PAGE_MARK_ORDER).toList();
            var count = 0L;
            PageMark previous = null;
            for (var mark : ordered) {
                if (previous != null
                        && Objects.equals(previous.path(), mark.path())
                        && mark.traceStart().toEpochMilli() - previous.traceStart().toEpochMilli()
                                <= PAGE_DUPLICATE_WINDOW_MS) {
                    continue;
                }
                count++;
                previous = mark;
            }
            return count;
        }
    }

    private record PageMark(String path, Instant traceStart, Integer stepIndex, String name) {}

    private static final Comparator<PageMark> PAGE_MARK_ORDER = Comparator
            .comparing(PageMark::traceStart)
            .thenComparing(mark -> mark.stepIndex() == null ? Integer.MAX_VALUE : mark.stepIndex())
            .thenComparing(PageMark::name);

    static AuditJourney toJourney(String uid, List<AuditRecord> records) {        var sorted = records.stream()
                .sorted(Comparator.comparing(AuditLogEndpoint::traceStart)
                        .thenComparing(record -> record.getStepIndex() == null
                                ? Integer.MAX_VALUE
                                : record.getStepIndex())
                        .thenComparing(record -> record.getMetadata().getName()))
                .toList();
        var start = sorted.stream()
                .map(AuditLogEndpoint::traceStart)
                .min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        var end = sorted.stream()
                .map(record -> record.getTimestamp() == null
                        ? Instant.EPOCH
                        : record.getTimestamp())
                .max(Comparator.naturalOrder())
                .orElse(start);
        var pageIndexes = new ArrayList<Integer>();
        for (var index = 0; index < sorted.size(); index++) {
            if (!isPage(sorted.get(index))) {
                continue;
            }
            var previousPageIndex = pageIndexes.isEmpty()
                    ? null
                    : pageIndexes.get(pageIndexes.size() - 1);
            if (previousPageIndex != null
                    && Objects.equals(pagePath(sorted.get(index)), pagePath(sorted.get(previousPageIndex)))
                    && traceStart(sorted.get(index)).toEpochMilli()
                                    - traceStart(sorted.get(previousPageIndex)).toEpochMilli()
                            <= PAGE_DUPLICATE_WINDOW_MS) {
                continue;
            }
            pageIndexes.add(index);
        }
        var steps = new ArrayList<AuditJourneyStep>();
        if (pageIndexes.isEmpty()) {
            steps.add(new AuditJourneyStep("SESSION", null, null, null, start, end, sorted));
        } else {
            var firstPageIndex = pageIndexes.get(0);
            if (firstPageIndex > 0) {
                steps.add(new AuditJourneyStep(
                        "SESSION",
                        null,
                        null,
                        pagePath(sorted.get(firstPageIndex)),
                        start,
                        traceStart(sorted.get(firstPageIndex)),
                        sorted.subList(0, firstPageIndex)));
            }
            for (var index = 0; index < pageIndexes.size(); index++) {
                var pageIndex = pageIndexes.get(index);
                var nextPageIndex = index + 1 < pageIndexes.size()
                        ? pageIndexes.get(index + 1)
                        : sorted.size();
                var page = sorted.get(pageIndex);
                var fromPath = index > 0
                        ? pagePath(sorted.get(pageIndexes.get(index - 1)))
                        : null;
                var toPath = index + 1 < pageIndexes.size()
                        ? pagePath(sorted.get(pageIndexes.get(index + 1)))
                        : null;
                var stepEnd = index + 1 < pageIndexes.size()
                        ? traceStart(sorted.get(pageIndexes.get(index + 1)))
                        : end;
                steps.add(new AuditJourneyStep(
                        "PAGE",
                        page,
                        fromPath,
                        toPath,
                        traceStart(page),
                        stepEnd,
                        sorted.subList(pageIndex + 1, nextPageIndex).stream()
                                .filter(record -> !isPage(record))
                                .toList()));
            }
        }
        var pages = pageIndexes.stream().map(sorted::get).toList();
        var pageCount = pages.size();
        var actionCount = steps.stream().mapToInt(step -> step.actions().size()).sum();
        var resourceTypes = sorted.stream()
                .map(AuditRecord::getResourceType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        var operations = sorted.stream()
                .map(AuditRecord::getOperation)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return new AuditJourney(
                uid,
                usernameOf(sorted),
                start,
                end,
                Math.max(0, Duration.between(start, end).toMillis()),
                pages.isEmpty() ? null : pagePath(pages.get(0)),
                pages.isEmpty() ? null : pagePath(pages.get(pages.size() - 1)),
                pageCount,
                actionCount,
                countSessions(sorted),
                pageCount > 0,
                journeyStatus(sorted),
                resourceTypes,
                operations,
                steps);
    }

    private static String pagePath(AuditRecord record) {
        if ("Page".equals(record.getResourceType()) && StringUtils.hasText(record.getResourceName())) {
            return record.getResourceName();
        }
        return record.getRequestPath();
    }

    private static boolean isPage(AuditRecord record) {
        if ("Page".equals(record.getResourceType())) {
            return true;
        }
        return "NAVIGATION".equals(record.getOperation())
                && StringUtils.hasText(record.getRequestPath())
                && !record.getRequestPath().startsWith("/apis/");
    }

    private static String usernameOf(List<AuditRecord> records) {
        return records.stream()
                .map(record -> contextString(record, "username"))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String contextString(AuditRecord record, String key) {
        var context = record.getContext();
        if (context == null) {
            return null;
        }
        var value = context.get(key);
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }

    private static int countSessions(List<AuditRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }
        var sessions = 1;
        for (var index = 1; index < records.size(); index++) {
            var previousEnd = records.get(index - 1).getTimestamp();
            if (previousEnd != null
                    && traceStart(records.get(index)).toEpochMilli() - previousEnd.toEpochMilli()
                            > JOURNEY_SESSION_GAP_MS) {
                sessions++;
            }
        }
        return sessions;
    }

    private static String journeyStatus(List<AuditRecord> records) {
        if (records.stream().anyMatch(record -> "DENIED".equals(record.getResult()))) {
            return "DENIED";
        }
        if (records.stream()
                .anyMatch(record -> isPage(record) && isFailure(record.getResult()))) {
            return "FAILED";
        }
        if (records.stream().anyMatch(record -> isFailure(record.getResult()))) {
            return "FAILURE";
        }
        return "SUCCESS";
    }

    private static String resultStatus(List<AuditRecord> records) {
        if (records.stream().anyMatch(record -> "DENIED".equals(record.getResult()))) {
            return "DENIED";
        }
        if (records.stream().anyMatch(record -> isFailure(record.getResult()))) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private static boolean isFailure(String result) {
        return "FAILURE".equals(result) || "FAILED".equals(result);
    }

    private static Instant traceStart(AuditRecord record) {
        if (record.getTimestamp() == null) {
            return Instant.EPOCH;
        }
        var durationMs = record.getDurationMs() == null ? 0L : Math.max(0L, record.getDurationMs());
        return record.getTimestamp().minusMillis(durationMs);
    }

    private static AuditStore.AuditQuery auditQuery(
            ServerRequest request, String cursor, int limit, boolean ascending) {
        return new AuditStore.AuditQuery(
                cursor,
                request.queryParam("from").filter(StringUtils::hasText).map(Instant::parse).orElse(null),
                request.queryParam("to").filter(StringUtils::hasText).map(Instant::parse).orElse(null),
                request.queryParam("uid").filter(StringUtils::hasText).orElse(null),
                request.queryParam("operation").filter(StringUtils::hasText).orElse(null),
                request.queryParam("result").filter(StringUtils::hasText).orElse(null),
                request.queryParam("traceId").filter(StringUtils::hasText).orElse(null),
                request.queryParam("traceFlowId").filter(StringUtils::hasText).orElse(null),
                limit,
                ascending);
    }

    private static int intParam(
            ServerRequest request, String name, int defaultValue, int min, int max) {
        return request.queryParam(name)
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new ServerWebInputException(
                                "查询参数 '" + name + "' 必须是整数。");
                    }
                })
                .map(value -> Math.max(min, Math.min(max, value)))
                .orElse(defaultValue);
    }

    private static boolean isAscending(ServerRequest request) {
        return "asc".equalsIgnoreCase(
                request.queryParam("order").filter(StringUtils::hasText).orElse(DEFAULT_ORDER));
    }

    private AuditTraceSummary toTraceSummary(List<AuditRecord> records) {
        return toTraceSummary(records, true);
    }

    private AuditTraceSummary toTraceSummary(List<AuditRecord> records, boolean includeRecords) {
        var sorted = records.stream()
                .sorted(Comparator.comparing(AuditLogEndpoint::traceStart)
                        .thenComparing(record -> record.getStepIndex() == null
                                ? Integer.MAX_VALUE
                                : record.getStepIndex())
                        .thenComparing(record -> record.getMetadata().getName()))
                .toList();
        var start = sorted.stream()
                .map(AuditLogEndpoint::traceStart)
                .min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        var end = sorted.stream()
                .map(record -> record.getTimestamp() == null
                        ? Instant.EPOCH
                        : record.getTimestamp())
                .max(Comparator.naturalOrder())
                .orElse(start);
        var status = resultStatus(sorted);
        var traceIds = sorted.stream()
                .map(AuditRecord::getTraceId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        var resourceTypes = sorted.stream()
                .map(AuditRecord::getResourceType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        var uid = sorted.stream()
                .filter(record -> !StringUtils.hasText(record.getParentAuditId()))
                .map(AuditRecord::getUid)
                .filter(StringUtils::hasText)
                .findFirst()
                .or(() -> sorted.stream()
                        .map(AuditRecord::getUid)
                        .filter(StringUtils::hasText)
                        .findFirst())
                .orElse(null);
        var traceFlowId = sorted.stream()
                .map(AuditRecord::getTraceFlowId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        var archived = sorted.stream().anyMatch(AuditRecord::isArchived);
        return new AuditTraceSummary(
                traceFlowId,
                traceIds,
                start,
                end,
                Math.max(0, Duration.between(start, end).toMillis()),
                status,
                archived,
                uid,
                sorted.size(),
                resourceTypes,
                includeRecords ? sorted : List.of());
    }

    private Mono<ServerResponse> archiveStats(ServerRequest request) {
        return auditLog
                .archiveStats()
                .map(stats -> new AuditArchiveStats(
                        stats.activeCount(),
                        stats.archivedCount(),
                        stats.activeCount() + stats.archivedCount()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> archives(ServerRequest request) {
        var page = intParam(request, "page", 1, 1, Integer.MAX_VALUE);
        var size = intParam(request, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        var query = auditQuery(request, null, size, false);
        return auditLog
                .archivePage(query, page, size)
                .map(result -> {
                    var items = result.items();
                    var hasMore = (long) page * size < result.total();
                    var nextCursor = items.isEmpty()
                            ? null
                            : items.get(items.size() - 1).getMetadata().getName();
                    return new AuditLogPage(
                            items, nextCursor, hasMore, result.total(), page, size);
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> archive(ServerRequest request) {
        return request.bodyToMono(AuditArchiveRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> {
                    if (body.before() == null) {
                        return Mono.error(new ServerWebInputException("归档截止时间不能为空。"));
                    }
                    if (body.before().isAfter(Instant.now())) {
                        return Mono.error(new ServerWebInputException("归档截止时间不能晚于当前时间。"));
                    }
                    return auditLog
                            .archiveBefore(body.before())
                            .map(count -> new AuditArchiveResult(count, body.before()));
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> graph(ServerRequest request) {
        var uid = request.queryParam("uid")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 'uid' 为必填项。"));
        var dateValue = request.queryParam("date")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 'date' 为必填项。"));
        final LocalDate date;
        try {
            date = LocalDate.parse(dateValue);
        } catch (Exception e) {
            throw new ServerWebInputException("查询参数 'date' 必须是 ISO 日期，例如 2026-11-14。");
        }
        var from = PudansTime.startOfDay(date);
        var to = from.plus(Duration.ofDays(1));
        return auditLog
                .findByUidBetween(uid, from, to)
                .collectList()
                .map(records -> toGraph(uid, date, records))
                .flatMap(graph -> ServerResponse.ok().bodyValue(graph));
    }

    private Mono<ServerResponse> trace(ServerRequest request) {
        var auditId = request.pathVariable("auditId");
        return auditLog
                .findById(auditId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "审计记录 [" + auditId + "] 不存在。")))
                .flatMap(focus -> {
                    Flux<AuditRecord> steps = StringUtils.hasText(focus.getTraceFlowId())
                            ? auditLog.findByTraceFlowId(focus.getTraceFlowId())
                            : Flux.just(focus);
                    return steps.collectList().map(list -> toTrace(auditId, list));
                })
                .flatMap(trace -> ServerResponse.ok().bodyValue(trace));
    }

    private Mono<ServerResponse> replay(ServerRequest request) {
        var traceFlowId = request.pathVariable("traceFlowId");
        return auditLog
                .findByTraceFlowId(traceFlowId)
                .collectList()
                .flatMap(records -> {
                    if (records.isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "追踪流 [" + traceFlowId + "] 不存在。"));
                    }
                    var steps = records.stream()
                            .sorted(Comparator.comparing(AuditLogEndpoint::traceStart)
                                    .thenComparing(record -> record.getStepIndex() == null
                                            ? Integer.MAX_VALUE
                                            : record.getStepIndex())
                                    .thenComparing(record -> record.getMetadata().getName()))
                            .map(this::toReplayStep)
                            .toList();
                    return ServerResponse.ok().bodyValue(new AuditReplay(traceFlowId, steps));
                });
    }

    private AuditGraph toGraph(String uid, LocalDate date, List<AuditRecord> records) {
        var nodes = records.stream()
                .sorted(Comparator.comparing(AuditRecord::getTimestamp)
                        .thenComparing(record -> record.getMetadata().getName()))
                .map(this::toNode)
                .toList();
        var nodeIds = nodes.stream().map(AuditNode::auditId).collect(java.util.stream.Collectors.toSet());
        var edges = records.stream()
                .filter(record -> StringUtils.hasText(record.getParentAuditId()))
                .map(record -> new AuditEdge(
                        record.getParentAuditId(),
                        record.getMetadata().getName(),
                        record.getTraceFlowId()))
                .toList();
        var roots = nodes.stream()
                .filter(node -> !StringUtils.hasText(node.parentAuditId()) || !nodeIds.contains(node.parentAuditId()))
                .map(AuditNode::auditId)
                .toList();
        return new AuditGraph(uid, date.toString(), nodes, edges, roots);
    }

    private AuditNode toNode(AuditRecord record) {
        return new AuditNode(
                record.getMetadata().getName(),
                record.getTraceFlowId(),
                record.getParentAuditId(),
                record.getStepIndex(),
                record.getOperation(),
                record.getResourceType(),
                record.getResourceName(),
                record.getResult(),
                record.getTimestamp(),
                record.isArchived());
    }

    private AuditTrace toTrace(String auditId, List<AuditRecord> records) {
        var sorted = records.stream()
                .sorted(Comparator.comparing(
                                (AuditRecord record) -> record.getStepIndex() == null
                                        ? Integer.MAX_VALUE
                                        : record.getStepIndex())
                        .thenComparing(AuditRecord::getTimestamp)
                        .thenComparing(record -> record.getMetadata().getName()))
                .toList();
        var steps = new ArrayList<TraceStep>();
        var before = new ArrayList<TraceStep>();
        var after = new ArrayList<TraceStep>();
        var focusSeen = false;
        for (var record : sorted) {
            var step = new TraceStep(
                    record.getMetadata().getName(),
                    record.getTraceFlowId(),
                    record.getParentAuditId(),
                    record.getStepIndex(),
                    record.getOperation(),
                    record.getResourceType(),
                    record.getResourceName(),
                    record.getResult(),
                    record.getTimestamp(),
                    record.isArchived(),
                    auditId.equals(record.getMetadata().getName()));
            steps.add(step);
            if (auditId.equals(record.getMetadata().getName())) {
                focusSeen = true;
            } else if (focusSeen) {
                after.add(step);
            } else {
                before.add(step);
            }
        }
        var traceFlowId = sorted.stream()
                .map(AuditRecord::getTraceFlowId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new AuditTrace(auditId, traceFlowId, steps, before, after);
    }

    private ReplayStep toReplayStep(AuditRecord record) {
        return new ReplayStep(
                record.getMetadata().getName(),
                record.getTraceFlowId(),
                record.getParentAuditId(),
                record.getUid(),
                record.getStepIndex(),
                record.getOperation(),
                record.getResourceType(),
                record.getResourceName(),
                record.getResult(),
                record.getTimestamp(),
                record.getRequestMethod(),
                record.getRequestPath(),
                record.getDurationMs(),
                record.getActionSnapshot(),
                record.isArchived());
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    public record AuditLogPage(
            List<AuditRecord> items,
            String nextCursor,
            boolean hasMore,
            Long total,
            Integer page,
            Integer size) {}


    public record AuditTraceSummary(
            String traceFlowId,
            List<String> traceIds,
            Instant start,
            Instant end,
            long durationMs,
            String status,
            boolean archived,
            String uid,
            int spanCount,
            List<String> resourceTypes,
            List<AuditRecord> records) {}


    public record AuditTracePage(
            List<AuditTraceSummary> items,
            long total,
            int page,
            int size,
            boolean truncated) {}


    public record AuditJourney(
            String uid,
            String username,
            Instant start,
            Instant end,
            long durationMs,
            String entryPath,
            String exitPath,
            int pageCount,
            int actionCount,
            int sessionCount,
            boolean complete,
            String status,
            List<String> resourceTypes,
            List<String> operations,
            List<AuditJourneyStep> steps) {

        AuditJourney withoutSteps() {
            return new AuditJourney(
                    uid,
                    username,
                    start,
                    end,
                    durationMs,
                    entryPath,
                    exitPath,
                    pageCount,
                    actionCount,
                    sessionCount,
                    complete,
                    status,
                    resourceTypes,
                    operations,
                    List.of());
        }
    }


    public record AuditJourneyStep(
            String kind,
            AuditRecord page,
            String fromPath,
            String toPath,
            Instant start,
            Instant end,
            List<AuditRecord> actions) {}


    public record AuditJourneyPage(
            List<AuditJourney> items,
            long total,
            int page,
            int size,
            boolean truncated,
            long pageViews,
            long actionCount) {}


    public record AuditNode(
            String auditId,
            String traceFlowId,
            String parentAuditId,
            Integer stepIndex,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            Instant timestamp,
            boolean archived) {}


    public record AuditEdge(String from, String to, String traceFlowId) {}


    public record AuditGraph(
            String uid, String date, List<AuditNode> nodes, List<AuditEdge> edges, List<String> roots) {}


    public record TraceStep(
            String auditId,
            String traceFlowId,
            String parentAuditId,
            Integer stepIndex,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            Instant timestamp,
            boolean archived,
            boolean focus) {}


    public record AuditTrace(
            String auditId, String traceFlowId, List<TraceStep> steps, List<TraceStep> before, List<TraceStep> after) {}


    public record AuditArchiveRequest(Instant before) {}


    public record AuditArchiveResult(long archivedCount, Instant before) {}


    public record AuditArchiveStats(long activeCount, long archivedCount, long totalCount) {}


    public record ReplayStep(
            String auditId,
            String traceFlowId,
            String parentAuditId,
            String uid,
            Integer stepIndex,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            Instant timestamp,
            String requestMethod,
            String requestPath,
            Long durationMs,
            String actionSnapshot,
            boolean archived) {}


    public record AuditReplay(String traceFlowId, List<ReplayStep> steps) {}
}
