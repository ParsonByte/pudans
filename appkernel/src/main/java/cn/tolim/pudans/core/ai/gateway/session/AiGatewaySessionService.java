package cn.tolim.pudans.core.ai.gateway.session;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.GatewaySession;
import cn.tolim.pudans.infra.r2dbc.ai.gateway.AiGatewaySessionRepository.NewGatewaySession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiGatewaySessionService {

    public static final String DEFAULT_SPACE = "default";

    private static final String CODE_SESSION_MISSING = "ai-gateway-session-missing";

    private final AiGatewaySessionRepository repository;

    private final AiTeamService teamService;

    private final AiSettingService settingService;

    public Mono<GatewaySession> bind(BindRequest request) {
        Assert.notNull(request, "会话绑定参数不能为空");
        Assert.hasText(request.userId(), "userId 不能为空");
        Assert.hasText(request.agentSource(), "agentSource 不能为空");
        Assert.hasText(request.sessionKey(), "sessionKey 不能为空");
        return resolveAgent(request.agentName(), request.userId())
                .flatMap(agent -> repository.upsert(new NewGatewaySession(
                        DEFAULT_SPACE,
                        request.userId(),
                        request.agentSource(),
                        request.sessionKey(),
                        request.sessionId(),
                        agent.getSpec().getTeam(),
                        agent.getMetadata().getName(),
                        request.taskId(),
                        request.conversationId())));
    }

    public Mono<GatewaySession> findBySession(
            String userId, String agentSource, String sessionKey) {
        return repository.findBySession(DEFAULT_SPACE, userId, agentSource, sessionKey);
    }

    public Mono<GatewaySession> requireSession(
            String userId, String agentSource, String sessionKey) {
        return findBySession(userId, agentSource, sessionKey)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.UNAUTHORIZED, CODE_SESSION_MISSING, "无法识别 Agent 会话。")));
    }

    public Mono<Boolean> unbind(String userId, String agentSource, String sessionKey) {
        return repository.deleteBySession(DEFAULT_SPACE, userId, agentSource, sessionKey);
    }

    private Mono<AiAgent> resolveAgent(@Nullable String agentName, String userId) {
        if (StringUtils.hasText(agentName)) {
            return teamService.requireAgent(agentName, userId);
        }
        return settingService
                .defaults()
                .map(defaults -> StringUtils.hasText(defaults.getAgent())
                        ? defaults.getAgent()
                        : AiTeamService.DEFAULT_AGENT)
                .flatMap(name -> teamService.requireAgent(name, userId));
    }

    public record BindRequest(
            String userId,
            String agentSource,
            String sessionKey,
            @Nullable String sessionId,
            @Nullable String agentName,
            @Nullable String taskId,
            @Nullable Long conversationId) {}
}
