package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.contract.agent.AgentApproval;
import cn.tolim.pudans.contract.agent.AgentRun;
import cn.tolim.pudans.contract.agent.dto.AgentApprovalDecisionRequest;
import cn.tolim.pudans.contract.agent.dto.AgentRunStartRequest;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuns {

    private static final String QUEUED = "queued";

    private static final String CANCELLED = "cancelled";

    private static final String FAILED = "failed";

    private final AgentRunRepository runs;

    private final AgentMessageRepository messages;

    private final AgentApprovalRepository approvals;

    private final AgentSessions sessions;

    private final ObjectProvider<AgentRuntime> runtimes;

    private final AgentRunEvents events;

    private Clock clock = Clock.systemUTC();

    // 写入用户消息和排队运行，再交给配置的运行时执行。
    public Mono<AgentRun> start(
            RequestIdentity identity,
            Collection<String> roles,
            AgentRunStartRequest request) {
        if (request == null
                || request.sessionId() == null
                || !StringUtils.hasText(request.content())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "agent-run-request-invalid",
                    "会话和消息内容不能为空。"));
        }
        var runtime = runtimes.getIfAvailable();
        if (runtime == null) {
            return Mono.error(runtimeMissing());
        }
        return sessions.require(identity, request.sessionId()).flatMap(session -> {
            var now = now();
            var message = AgentMessageEntity.userMessage(
                    session.getId(),
                    null,
                    Map.of("type", "text", "text", request.content().trim()),
                    now);
            return messages
                    .save(message)
                    .flatMap(saved -> {
                        var run = AgentRunEntity.queued(
                                session.getId(),
                                saved.getId(),
                                modelId(request, session),
                                request.answerMode(),
                                request.reasoning(),
                                request.toolIds(),
                                now);
                        saved.setRunId(run.getId());
                        return messages
                                .save(saved)
                                .then(runs.save(run));
                    })
                    .flatMap(run -> sessions
                            .touch(identity, session.getId(), now.toInstant())
                            .then(events.publish(
                                    run.getId(),
                                    run.getSessionId(),
                                    AgentRunEvents.RUN_QUEUED,
                                    Map.of("status", QUEUED)))
                            .thenReturn(run));
        }).flatMap(run -> runtime
                .start(new AgentRunContext(
                        run.getId(),
                        run.getSessionId(),
                        identity.ownerId(),
                        identity.workspaceId(),
                        run.getModelId(),
                        run.getAnswerMode(),
                        run.getReasoning(),
                        request.toolIds() == null ? List.of() : request.toolIds(),
                        roles(roles),
                        request.content().trim()))
                .onErrorResume(error -> markFailed(run, error))
                .thenReturn(run.toContract()));
    }

    // 按归属读取运行状态。
    public Mono<AgentRun> status(RequestIdentity identity, UUID runId) {
        return require(identity, runId).map(AgentRunEntity::toContract);
    }

    // 请求取消运行，渠道受理前直接终止并保留取消标记。
    public Mono<AgentRun> cancel(
            RequestIdentity identity, Collection<String> roles, UUID runId) {
        return require(identity, runId).flatMap(run -> {
            if (terminal(run.getStatus())) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "agent-run-terminal", "运行已经结束，不能取消。"));
            }
            run.setCancelRequested(true);
            var cancelledBeforeProvider = !run.isProviderRequestSent();
            if (cancelledBeforeProvider) {
                run.setStatus(CANCELLED);
                run.setFinishedAt(now());
            }
            return runs.save(run).flatMap(saved -> {
                var runtime = runtimes.getIfAvailable();
                if (runtime == null) {
                    return cancelledBeforeProvider
                            ? publishCancelled(saved)
                            : Mono.just(saved.toContract());
                }
                var context = context(saved, identity, roles);
                return runtime.cancel(context)
                        .flatMap(active -> active
                                ? runs.findById(saved.getId()).map(AgentRunEntity::toContract)
                                : cancelledBeforeProvider
                                        ? publishCancelled(saved)
                                        : Mono.just(saved.toContract()));
            });
        });
    }

    // 对待处理审批写入当前用户的决定。
    public Mono<AgentApproval> decide(
            RequestIdentity identity,
            Collection<String> roles,
            UUID runId,
            UUID approvalId,
            AgentApprovalDecisionRequest request) {
        if (request == null || !StringUtils.hasText(request.decision())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "agent-approval-decision-required", "审批决定不能为空。"));
        }
        var decision = request.decision().trim().toLowerCase();
        if (!decision.equals("approve") && !decision.equals("reject")) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "agent-approval-decision-invalid", "审批决定必须为 approve 或 reject。"));
        }
        var runtime = runtimes.getIfAvailable();
        if (runtime == null) {
            return Mono.error(runtimeMissing());
        }
        return require(identity, runId).flatMap(run -> approvals
                .findByIdAndRunId(approvalId, runId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "agent-approval-not-found", "审批请求不存在。")))
                .flatMap(approval -> {
                    if (terminal(run.getStatus())) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT, "agent-run-terminal", "运行已经结束，不能处理审批。"));
                    }
                    if (!"pending".equals(approval.getStatus())) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT, "agent-approval-decided", "审批请求已经处理。"));
                    }
                    var now = now();
                    if (approval.getExpiresAt() != null && !approval.getExpiresAt().isAfter(now)) {
                        approval.setStatus("expired");
                        return approvals.save(approval).flatMap(expired -> Mono.error(new ApiException(
                                HttpStatus.CONFLICT, "agent-approval-expired", "审批请求已经过期。")));
                    }
                    var approved = decision.equals("approve");
                    var context = context(run, identity, roles);
                    return runtime
                            .resume(context, approvalId, approved)
                            .switchIfEmpty(Mono.just(false))
                            .flatMap(resumed -> {
                                if (!resumed) {
                                    return Mono.error(new ApiException(
                                            HttpStatus.CONFLICT,
                                            "agent-approval-resume-unavailable",
                                            "当前实例没有该审批对应的 AgentScope 暂停状态。"));
                                }
                                approval.setStatus(approved ? "approved" : "rejected");
                                approval.setDecidedBy(identity.ownerId());
                                approval.setDecidedAt(now);
                                approval.setDecisionJson(JsonUtils.objectToJson(Map.of(
                                        "decision", decision,
                                        "details", request.details() == null
                                                ? Map.of()
                                                : request.details())));
                                return approvals
                                        .save(approval)
                                        .map(AgentApprovalEntity::toContract)
                                        .onErrorResume(error -> runtime
                                                .cancel(context)
                                                .onErrorResume(cancelError -> {
                                                    log.warn(
                                                            "审批状态写入失败且无法取消运行：run={} approval={}",
                                                            runId,
                                                            approvalId,
                                                            cancelError);
                                                    return Mono.empty();
                                                })
                                                .then(Mono.error(error)));
                            });
                }));
    }

    // 读取运行并校验所属会话归属。
    private Mono<AgentRunEntity> require(RequestIdentity identity, UUID runId) {
        return runs
                .findById(runId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "agent-run-not-found", "Agent 运行不存在。")))
                .flatMap(run -> sessions
                        .require(identity, run.getSessionId())
                        .thenReturn(run));
    }

    // 优先使用会话默认模型。
    private static UUID modelId(AgentRunStartRequest request, AgentSessionEntity session) {
        return request.modelId() == null ? session.getDefaultModelId() : request.modelId();
    }

    private static AgentRunContext context(
            AgentRunEntity run, RequestIdentity identity, Collection<String> roles) {
        return new AgentRunContext(
                run.getId(),
                run.getSessionId(),
                identity.ownerId(),
                identity.workspaceId(),
                run.getModelId(),
                run.getAnswerMode(),
                run.getReasoning(),
                List.of(),
                roles(roles),
                null);
    }

    // 运行启动失败时保存稳定错误状态。
    private Mono<Void> markFailed(AgentRunEntity run, Throwable error) {
        var code = error instanceof ApiException api ? api.getCode() : "agent-runtime-failed";
        return runs.findById(run.getId())
                .defaultIfEmpty(run)
                .flatMap(current -> {
                    if (terminal(current.getStatus())) {
                        return Mono.empty();
                    }
                    current.setStatus(FAILED);
                    current.setErrorCode(code);
                    current.setErrorMessage(StringUtils.hasText(error.getMessage())
                            ? error.getMessage()
                            : "Agent 运行时失败。");
                    current.setFinishedAt(now());
                    var data = new LinkedHashMap<String, Object>();
                    data.put("status", FAILED);
                    data.put("errorCode", code);
                    data.put("errorMessage", current.getErrorMessage());
                    return runs.save(current)
                            .then(events.publish(
                                    current.getId(),
                                    current.getSessionId(),
                                    AgentRunEvents.RUN_FAILED,
                                    data));
                })
                .then();
    }

    private Mono<AgentRun> publishCancelled(AgentRunEntity run) {
        return events
                .publish(
                        run.getId(),
                        run.getSessionId(),
                        AgentRunEvents.RUN_CANCELLED,
                        Map.of("status", CANCELLED, "reason", "cancel-requested"))
                .thenReturn(run.toContract());
    }

    // 已结束状态不再接受取消。
    private static boolean terminal(String status) {
        return "completed".equals(status)
                || FAILED.equals(status)
                || CANCELLED.equals(status);
    }

    private static ApiException runtimeMissing() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "agent-runtime-not-configured",
                "Agent 运行时尚未配置。");
    }

    private static Set<String> roles(Collection<String> roles) {
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
