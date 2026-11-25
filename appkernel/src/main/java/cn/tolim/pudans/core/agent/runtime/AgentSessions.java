package cn.tolim.pudans.core.agent.runtime;

import cn.tolim.pudans.contract.agent.AgentMessage;
import cn.tolim.pudans.contract.agent.AgentSession;
import cn.tolim.pudans.contract.agent.dto.AgentMessagePage;
import cn.tolim.pudans.contract.agent.dto.AgentSessionCreateRequest;
import cn.tolim.pudans.contract.agent.dto.AgentSessionPage;
import cn.tolim.pudans.contract.agent.dto.AgentSessionSummary;
import cn.tolim.pudans.contract.agent.dto.AgentSessionUpdateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionEntity;
import cn.tolim.pudans.infra.r2dbc.agent.AgentSessionRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AgentSessions {

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private static final String DEFAULT_TITLE = "新会话";

    private final AgentSessionRepository sessions;

    private final AgentMessageRepository messages;

    private Clock clock = Clock.systemUTC();

    // 列出当前用户在工作区内的会话。
    public Mono<AgentSessionPage> list(RequestIdentity identity, int limit) {
        var pageSize = normalizeLimit(limit);
        return sessions
                .findByOwnerIdAndWorkspaceIdOrderByUpdatedAtDesc(
                        identity.ownerId(),
                        identity.workspaceId(),
                        PageRequest.of(0, pageSize))
                .map(AgentSessionEntity::toContract)
                .collectList()
                .map(items -> new AgentSessionPage(items, pageSize));
    }

    // 汇总当前用户的会话数量和最近消息时间。
    public Mono<AgentSessionSummary> summary(RequestIdentity identity) {
        return Mono.zip(
                        sessions.countByOwnerIdAndWorkspaceId(identity.ownerId(), identity.workspaceId()),
                        sessions.countByOwnerIdAndWorkspaceIdAndStatus(
                                identity.ownerId(), identity.workspaceId(), "active"),
                        sessions.findFirstByOwnerIdAndWorkspaceIdOrderByLastMessageAtDesc(
                                identity.ownerId(), identity.workspaceId())
                                .switchIfEmpty(Mono.just(new AgentSessionEntity())))
                .map(tuple -> new AgentSessionSummary(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3().getLastMessageAt() == null
                                ? null
                                : tuple.getT3().getLastMessageAt().toInstant()));
    }

    // 按归属读取会话详情。
    public Mono<AgentSession> view(RequestIdentity identity, UUID sessionId) {
        return require(identity, sessionId).map(AgentSessionEntity::toContract);
    }

    // 创建归属于当前身份的新会话。
    public Mono<AgentSession> create(RequestIdentity identity, AgentSessionCreateRequest request) {
        var body = request == null ? new AgentSessionCreateRequest(null, null, null) : request;
        var title = StringUtils.hasText(body.title()) ? body.title().trim() : DEFAULT_TITLE;
        var profile = StringUtils.hasText(body.profile()) ? body.profile().trim() : null;
        var entity = AgentSessionEntity.create(
                identity.ownerId(),
                identity.workspaceId(),
                title,
                profile,
                body.defaultModelId(),
                now());
        return sessions.save(entity).map(AgentSessionEntity::toContract);
    }

    // 更新会话可编辑元数据并保持归属不变。
    public Mono<AgentSession> updateMetadata(
            RequestIdentity identity, UUID sessionId, AgentSessionUpdateRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "agent-session-body-required", "会话更新内容不能为空。"));
        }
        return require(identity, sessionId).flatMap(session -> {
            if (request.title() != null) {
                session.setTitle(StringUtils.hasText(request.title()) ? request.title().trim() : DEFAULT_TITLE);
            }
            if (request.profile() != null) {
                session.setProfile(StringUtils.hasText(request.profile()) ? request.profile().trim() : null);
            }
            if (request.defaultModelId() != null) {
                session.setDefaultModelId(request.defaultModelId());
            }
            session.setUpdatedAt(now());
            return sessions.save(session);
        }).map(AgentSessionEntity::toContract);
    }

    // 归档会话但不删除历史消息与运行记录。
    public Mono<AgentSession> archive(RequestIdentity identity, UUID sessionId) {
        return require(identity, sessionId).flatMap(session -> {
            session.setStatus("archived");
            session.setUpdatedAt(now());
            return sessions.save(session);
        }).map(AgentSessionEntity::toContract);
    }

    // 按时间游标倒序读取会话消息。
    public Mono<AgentMessagePage> messages(
            RequestIdentity identity, UUID sessionId, String before, int limit) {
        var pageSize = normalizeLimit(limit);
        var cursor = parseCursor(before);
        return require(identity, sessionId)
                .flatMapMany(session -> cursor == null
                        ? messages.findBySessionIdOrderByCreatedAtDesc(
                                sessionId, PageRequest.of(0, pageSize))
                        : messages.findBySessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                                sessionId, cursor, PageRequest.of(0, pageSize)))
                .map(AgentMessageEntity::toContract)
                .collectList()
                .map(items -> new AgentMessagePage(items, nextCursor(items, pageSize)));
    }

    // 运行完成后推进会话的最近消息时间。
    public Mono<Void> touch(RequestIdentity identity, UUID sessionId, Instant occurredAt) {
        return require(identity, sessionId)
                .flatMap(session -> {
                    session.setLastMessageAt(toOffset(occurredAt));
                    session.setUpdatedAt(toOffset(occurredAt));
                    return sessions.save(session);
                })
                .then();
    }

    // 读取并校验会话归属。
    public Mono<AgentSessionEntity> require(RequestIdentity identity, UUID sessionId) {
        return sessions
                .findByIdAndOwnerIdAndWorkspaceId(sessionId, identity.ownerId(), identity.workspaceId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "agent-session-not-found", "Agent 会话不存在。")));
    }

    // 限制列表大小并拒绝非正数。
    public static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    // 解析可选的消息时间游标。
    private static OffsetDateTime parseCursor(String before) {
        if (!StringUtils.hasText(before)) {
            return null;
        }
        try {
            return toOffset(Instant.parse(before));
        } catch (DateTimeParseException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "agent-message-cursor-invalid", "消息游标不是有效时间。");
        }
    }

    // 仅在整页返回时提供下一页时间游标。
    private static String nextCursor(List<AgentMessage> items, int limit) {
        if (items.size() < limit || items.isEmpty()) {
            return null;
        }
        var last = items.getLast();
        return last.createdAt() == null ? null : last.createdAt().toString();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private OffsetDateTime now() {
        return toOffset(clock.instant());
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
