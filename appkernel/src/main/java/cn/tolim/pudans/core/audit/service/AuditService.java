package cn.tolim.pudans.core.audit.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.audit.AuditStore;
import cn.tolim.pudans.infra.utils.IpAddressUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String TRACE_ID_ATTRIBUTE = AuditService.class.getName() + ".TRACE_ID";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_FLOW_ID_ATTRIBUTE = AuditService.class.getName() + ".TRACE_FLOW_ID";
    public static final String TRACE_FLOW_ID_HEADER = "X-Trace-Flow-Id";
    public static final String PARENT_AUDIT_ID_ATTRIBUTE = AuditService.class.getName() + ".PARENT_AUDIT_ID";
    public static final String PARENT_AUDIT_ID_HEADER = "X-Parent-Audit-Id";
    public static final String AUDIT_ID_ATTRIBUTE = AuditService.class.getName() + ".AUDIT_ID";
    public static final String STEP_INDEX_ATTRIBUTE = AuditService.class.getName() + ".STEP_INDEX";
    public static final String AUDIT_TRAIL_ATTRIBUTE = AuditService.class.getName() + ".AUDIT_TRAIL";
    public static final String USERNAME_ATTRIBUTE = AuditService.class.getName() + ".USERNAME";
    public static final String PAT_OWNER_ATTRIBUTE = AuditService.class.getName() + ".PAT_OWNER";
    public static final String SUDO_USER_ATTRIBUTE = AuditService.class.getName() + ".SUDO_USER";
    public static final String UID_ATTRIBUTE = AuditService.class.getName() + ".UID";
    public static final String OPERATION_ATTRIBUTE = AuditService.class.getName() + ".OPERATION";
    public static final String RESULT_ATTRIBUTE = AuditService.class.getName() + ".RESULT";
    public static final String RESOURCE_TYPE_ATTRIBUTE = AuditService.class.getName() + ".RESOURCE_TYPE";
    public static final String RESOURCE_NAME_ATTRIBUTE = AuditService.class.getName() + ".RESOURCE_NAME";
    public static final String CONTEXT_ATTRIBUTE = AuditService.class.getName() + ".CONTEXT";

    private final AuditStore auditStore;

    private final ReactiveExtensionClient client;

    private final AuditWal auditWal;

    private static final int QUEUE_CAPACITY = 10_000;

    private static final int TRACE_ID_LENGTH = 64;

    private static final int AUDIT_ID_LENGTH = 64;

    private static final int UID_LENGTH = 128;

    private static final int OPERATION_LENGTH = 64;

    private static final int RESOURCE_TYPE_LENGTH = 128;

    private static final int RESOURCE_NAME_LENGTH = 512;

    private static final int REQUEST_METHOD_LENGTH = 16;

    private static final int REQUEST_PATH_LENGTH = 2048;

    private static final int IP_LENGTH = 128;

    private static final int USER_AGENT_LENGTH = 1024;

    private static final int RESULT_LENGTH = 32;

    private final Sinks.Many<AuditRecord> sink =
            Sinks.many().unicast().onBackpressureBuffer(new java.util.concurrent.LinkedBlockingQueue<>(QUEUE_CAPACITY));

    private final Object emitLock = new Object();

    private volatile CountDownLatch drained;

    private Disposable writer;

    @PostConstruct
    public void start() {
        var latch = new CountDownLatch(1);
        this.drained = latch;
        writer = sink.asFlux()
                .flatMap(record -> auditStore
                        .append(record)
                        .onErrorResume(e -> {
                            log.error(
                                    "无法持久化审计记录 {}，正在写入本地 WAL",
                                    metadataName(record),
                                    e);
                            auditWal.append(record);
                            return Mono.empty();
                        }), 4)
                .doFinally(signal -> latch.countDown())
                .subscribe();
        auditWal.replay(this::record);
    }

    @PreDestroy
    public void stop() {
        sink.tryEmitComplete();
        var latch = this.drained;
        if (latch != null) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    log.warn("排空审计写入队列时超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (writer != null) {
            writer.dispose();
        }
    }

    public AuditRecord newRecord(String traceId, String uid, String operation) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName(AuditIdGenerator.next());
        record.setMetadata(metadata);
        record.setTraceId(traceId);
        record.setUid(uid);
        record.setOperation(operation);
        record.setTimestamp(Instant.now());
        record.setStepIndex(1);
        record.setContext(new LinkedHashMap<>());
        return record;
    }

    public void record(AuditRecord record) {
        if (record == null) {
            return;
        }
        normalize(record);
        Sinks.EmitResult result;
        synchronized (emitLock) {
            result = sink.tryEmitNext(record);
        }
        if (result.isFailure()) {
            log.error(
                    "审计写入器拒绝了记录 {}（{}），将改为写入本地 WAL",
                    metadataName(record),
                    result);
            writeToWal(record);
        }
    }

    private void writeToWal(AuditRecord record) {
        try {
            Schedulers.boundedElastic().schedule(() -> auditWal.append(record));
        } catch (RejectedExecutionException e) {
            auditWal.append(record);
        }
    }

    private void normalize(AuditRecord record) {
        if (record.getTraceFlowId() == null || record.getTraceFlowId().isBlank()) {
            record.setTraceFlowId(AuditIdGenerator.next());
            record.markSyntheticFlow();
        }
        record.setTraceId(cut(record.getTraceId(), TRACE_ID_LENGTH));
        record.setTraceFlowId(cut(record.getTraceFlowId(), AUDIT_ID_LENGTH));
        record.setParentAuditId(cut(record.getParentAuditId(), AUDIT_ID_LENGTH));
        record.setUid(cut(record.getUid(), UID_LENGTH));
        record.setOperation(cut(record.getOperation(), OPERATION_LENGTH));
        record.setResourceType(cut(record.getResourceType(), RESOURCE_TYPE_LENGTH));
        record.setResourceName(cut(record.getResourceName(), RESOURCE_NAME_LENGTH));
        record.setRequestMethod(cut(record.getRequestMethod(), REQUEST_METHOD_LENGTH));
        record.setRequestPath(cut(record.getRequestPath(), REQUEST_PATH_LENGTH));
        record.setIp(cut(record.getIp(), IP_LENGTH));
        record.setUserAgent(cut(record.getUserAgent(), USER_AGENT_LENGTH));
        record.setResult(cut(record.getResult(), RESULT_LENGTH));
    }

    private static String cut(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String metadataName(AuditRecord record) {
        return record.getMetadata() == null ? null : record.getMetadata().getName();
    }

    public void record(
            ServerWebExchange exchange,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            long durationMs,
            Map<String, Object> context) {
        var record = newRecord(
                traceId(exchange), (String) exchange.getAttributes().get(UID_ATTRIBUTE), operation);
        var explicitId = (String) exchange.getAttributes().get(AUDIT_ID_ATTRIBUTE);
        if (explicitId != null && !explicitId.isBlank()) {
            record.getMetadata().setName(explicitId);
        }
        record.setTraceFlowId((String) exchange.getAttributes().get(TRACE_FLOW_ID_ATTRIBUTE));
        record.setParentAuditId((String) exchange.getAttributes().get(PARENT_AUDIT_ID_ATTRIBUTE));
        var stepIndex = (Integer) exchange.getAttributes().get(STEP_INDEX_ATTRIBUTE);
        record.setStepIndex(stepIndex == null ? 1 : stepIndex);
        record.setResourceType(resourceType);
        record.setResourceName(resourceName);
        record.setRequestMethod(exchange.getRequest().getMethod().name());
        record.setRequestPath(exchange.getRequest().getPath().value());
        record.setIp(clientIp(exchange));
        record.setUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
        record.setResult(result);
        record.setDurationMs(durationMs);
        if (context != null) {
            record.setContext(context);
            record.setActionSnapshot(JsonUtils.objectToJson(context));
        }
        record(record);
    }

    

    public Mono<Void> recordChild(
            ServerWebExchange exchange,
            String uid,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            Map<String, Object> actionSnapshot,
            String parentAuditId) {
        return Mono.deferContextual(contextView -> {
            var trail = contextView.getOrDefault(
                    AuditTrail.class, (AuditTrail) exchange.getAttributes().get(AUDIT_TRAIL_ATTRIBUTE));
            if (trail == null) {
                return Mono.empty();
            }
            var actorUid = StringUtils.hasText(uid) ? Mono.just(uid) : resolveActorUid(exchange);
            return actorUid.flatMap(resolvedUid -> {
                var record = newRecord(traceId(exchange), resolvedUid, operation);
                record.setTraceFlowId(trail.getTraceFlowId());
                record.setParentAuditId(
                        parentAuditId == null || parentAuditId.isBlank()
                                ? trail.getRootAuditId()
                                : parentAuditId);
                record.setStepIndex(trail.nextStep());
                record.setResourceType(resourceType);
                record.setResourceName(resourceName);
                record.setRequestMethod(exchange.getRequest().getMethod().name());
                record.setRequestPath(exchange.getRequest().getPath().value());
                record.setIp(clientIp(exchange));
                record.setUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
                record.setResult(result);
                record.setDurationMs(0L);
                if (actionSnapshot != null) {
                    record.setActionSnapshot(JsonUtils.objectToJson(actionSnapshot));
                }
                record(record);
                return Mono.empty();
            });
        });
    }

    public Flux<AuditRecord> findByTraceFlowId(String traceFlowId) {
        return auditStore.findByTraceFlowId(traceFlowId);
    }

    public Flux<AuditRecord> findByUidBetween(String uid, Instant from, Instant to) {
        return auditStore.findByUidBetween(uid, from, to);
    }

    public Mono<AuditRecord> findById(String auditId) {
        return auditStore.findById(auditId);
    }

    public static String traceId(ServerWebExchange exchange) {
        return (String) exchange.getAttributes()
                .computeIfAbsent(TRACE_ID_ATTRIBUTE, key -> AuditIdGenerator.next());
    }

    public Flux<AuditRecord> query(AuditStore.AuditQuery query) {
        return auditStore.query(query);
    }

    public Mono<AuditStore.AuditPageResult> page(AuditStore.AuditQuery query, int page, int size) {
        return auditStore.page(query, page, size);
    }

    public Mono<AuditStore.TraceFlowPage> traceFlows(AuditStore.AuditQuery query, int page, int size) {
        return auditStore.traceFlows(query, page, size);
    }

    public Mono<Long> countSince(Instant from) {
        return auditStore.countSince(from);
    }

    public Mono<Map<java.time.LocalDate, Long>> countPerDay(
            Instant since, String operation, String result) {
        return countPerDay(since, null, operation, result);
    }

    public Mono<Map<java.time.LocalDate, Long>> countPerDay(
            Instant since, String uid, String operation, String result) {
        return auditStore.countPerDay(since, uid, operation, result);
    }

    public Flux<AuditRecord> findRecent(int limit) {
        return auditStore.findRecent(limit);
    }

    private Mono<String> resolveActorUid(ServerWebExchange exchange) {
        var uid = (String) exchange.getAttributes().get(UID_ATTRIBUTE);
        if (StringUtils.hasText(uid)) {
            return Mono.just(uid);
        }
        var username = (String) exchange.getAttributes().get(USERNAME_ATTRIBUTE);
        if (!StringUtils.hasText(username)) {
            return Mono.just("anonymous");
        }
        return client.fetch(User.class, username)
                .map(user -> user.getSpec().getUid())
                .defaultIfEmpty("unknown");
    }

    private static String clientIp(ServerWebExchange exchange) {
        return IpAddressUtils.getClientIp(exchange.getRequest());
    }
}
