package cn.tolim.pudans.core.audit.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.infra.audit.AuditStore;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    private static final String DEFAULT_ORDER = "desc";

    private final AuditService auditService;

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
        var limit = Math.max(
                1,
                Math.min(
                        MAX_LIMIT,
                        request.queryParam("limit")
                                .filter(StringUtils::hasText)
                                .map(Integer::parseInt)
                                .orElse(DEFAULT_LIMIT)));
        var ascending = "asc".equalsIgnoreCase(request.queryParam("order").filter(StringUtils::hasText).orElse(DEFAULT_ORDER));
        var query = new AuditStore.AuditQuery(
                request.queryParam("cursor").orElse(null),
                request.queryParam("from").filter(StringUtils::hasText).map(Instant::parse).orElse(null),
                request.queryParam("to").filter(StringUtils::hasText).map(Instant::parse).orElse(null),
                request.queryParam("uid").orElse(null),
                request.queryParam("operation").orElse(null),
                request.queryParam("result").orElse(null),
                request.queryParam("traceId").orElse(null),
                request.queryParam("traceFlowId").orElse(null),
                limit + 1,
                ascending);
        return auditService
                .query(query)
                .take(limit + 1L)
                .collectList()
                .map(records -> {
                    var hasMore = records.size() > limit;
                    var items = hasMore ? records.subList(0, limit) : records;
                    var nextCursor = items.isEmpty()
                            ? null
                            : items.get(items.size() - 1).getMetadata().getName();
                    return new AuditLogPage(items, nextCursor, hasMore);
                })
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> graph(ServerRequest request) {
        var uid = request.queryParam("uid")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("Query param 'uid' is required."));
        var dateValue = request.queryParam("date")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("Query param 'date' is required."));
        final LocalDate date;
        try {
            date = LocalDate.parse(dateValue);
        } catch (Exception e) {
            throw new ServerWebInputException("Query param 'date' must be an ISO date, e.g. 2026-11-14.");
        }
        var from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = from.plus(Duration.ofDays(1));
        return auditService
                .findByUidBetween(uid, from, to)
                .collectList()
                .map(records -> toGraph(uid, date, records))
                .flatMap(graph -> ServerResponse.ok().bodyValue(graph));
    }

    private Mono<ServerResponse> trace(ServerRequest request) {
        var auditId = request.pathVariable("auditId");
        return auditService
                .findById(auditId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Audit record [" + auditId + "] was not found.")))
                .flatMap(focus -> {
                    Flux<AuditRecord> steps = StringUtils.hasText(focus.getTraceFlowId())
                            ? auditService.findByTraceFlowId(focus.getTraceFlowId())
                            : Flux.just(focus);
                    return steps.collectList().map(list -> toTrace(auditId, list));
                })
                .flatMap(trace -> ServerResponse.ok().bodyValue(trace));
    }

    private Mono<ServerResponse> replay(ServerRequest request) {
        var traceFlowId = request.pathVariable("traceFlowId");
        return auditService
                .findByTraceFlowId(traceFlowId)
                .collectList()
                .flatMap(records -> {
                    if (records.isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Trace flow [" + traceFlowId + "] was not found."));
                    }
                    var steps = records.stream()
                            .sorted(Comparator.comparing(
                                            (AuditRecord record) -> record.getStepIndex() == null
                                                    ? Integer.MAX_VALUE
                                                    : record.getStepIndex())
                                    .thenComparing(AuditRecord::getTimestamp)
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
                record.getStepIndex(),
                record.getOperation(),
                record.getResourceType(),
                record.getResourceName(),
                record.getResult(),
                record.getTimestamp(),
                record.getActionSnapshot(),
                record.isArchived());
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }


    public record AuditLogPage(List<AuditRecord> items, String nextCursor, boolean hasMore) {}


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


    public record ReplayStep(
            String auditId,
            String traceFlowId,
            String parentAuditId,
            Integer stepIndex,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            Instant timestamp,
            String actionSnapshot,
            boolean archived) {}


    public record AuditReplay(String traceFlowId, List<ReplayStep> steps) {}
}
