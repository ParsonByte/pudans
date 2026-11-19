package cn.tolim.pudans.core.ai.team;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiTeamService {

    public static final String DEFAULT_TEAM = "default";

    public static final String DEFAULT_AGENT = "default";

    private final ReactiveExtensionClient client;

    private final AiSettingService settingService;

    private final UserService userService;

    public Mono<AiTeam> getTeam(String name) {
        return client.get(AiTeam.class, name);
    }

    public Mono<AiAgent> getAgent(String name) {
        return client.get(AiAgent.class, name);
    }

    public Mono<AiTeam> defaultTeam() {
        return settingService.defaults().flatMap(defaults -> getTeam(
                StringUtils.hasText(defaults.getTeam()) ? defaults.getTeam() : DEFAULT_TEAM));
    }

    public Mono<AiAgent> defaultAgent() {
        return settingService.defaults().flatMap(defaults -> getAgent(
                StringUtils.hasText(defaults.getAgent()) ? defaults.getAgent() : DEFAULT_AGENT));
    }

    // 默认团队是未指定团队时的共享作用域，对所有已认证用户开放。
    public Mono<Boolean> isMember(String teamName, String userId) {
        return getTeam(teamName)
                .map(team -> team.getSpec() != null
                        && team.getSpec().getStatus() == AiTeam.TeamStatus.ACTIVE
                        && (DEFAULT_TEAM.equals(teamName) || hasActiveMember(team, userId)))
                .defaultIfEmpty(false);
    }

    private static boolean hasActiveMember(AiTeam team, String userId) {
        return team.getSpec() != null
                && team.getSpec().getMembers() != null
                && team.getSpec().getMembers().stream().anyMatch(member -> userId.equals(member.getUserId())
                        && member.getStatus() == AiTeam.MemberStatus.ACTIVE);
    }

    public Mono<AiAgent> requireAgent(String agentName, String userId) {
        return getAgent(agentName).flatMap(agent -> {
            var spec = agent.getSpec();
            if (spec == null || !StringUtils.hasText(spec.getTeam())) {
                return Mono.error(new AccessDeniedException("Agent 未绑定团队。"));
            }
            if (spec.getStatus() != AiAgent.AgentStatus.ACTIVE) {
                return Mono.error(new AccessDeniedException("Agent 已停用。"));
            }
            return isMember(spec.getTeam(), userId).flatMap(member -> {
                if (!Boolean.TRUE.equals(member)) {
                    return Mono.error(new AccessDeniedException("无权访问该 Agent。"));
                }
                if (spec.getVisibility() == AiAgent.Visibility.PRIVATE
                        && !userId.equals(spec.getOwnerUserId())) {
                    return Mono.error(new AccessDeniedException("无权访问该 Agent。"));
                }
                return Mono.just(agent);
            });
        });
    }

    public Mono<AiTeam> requireTeam(String teamName, String userId) {
        return isMember(teamName, userId).flatMap(member -> Boolean.TRUE.equals(member)
                ? getTeam(teamName)
                : Mono.error(new AccessDeniedException("无权访问该团队。")));
    }

    public Mono<AiTeam> addMember(String teamName, String userId, AiTeam.TeamRole role) {
        return requireUser(userId)
                .then(Mono.defer(() -> getTeam(teamName)))
                .flatMap(team -> {
                    var current = team.getSpec();
                    var spec = current == null ? new AiTeam.Spec() : current;
                    team.setSpec(spec);
                    if (spec.getMembers() == null) {
                        spec.setMembers(new java.util.ArrayList<>());
                    }
                    var member = spec.getMembers().stream()
                            .filter(existing -> userId.equals(existing.getUserId()))
                            .findFirst()
                            .orElseGet(() -> {
                                var created = new AiTeam.Member();
                                created.setUserId(userId);
                                spec.getMembers().add(created);
                                return created;
                            });
                    member.setRole(role == null ? AiTeam.TeamRole.MEMBER : role);
                    member.setStatus(AiTeam.MemberStatus.ACTIVE);
                    return client.update(team);
                });
    }

    private Mono<Void> requireUser(String userId) {
        if (BuiltinEntities.SYSTEM_USER.equals(userId)) {
            return Mono.empty();
        }
        return userService
                .getUserByUid(userId)
                .switchIfEmpty(Mono.error(new ServerWebInputException("团队成员用户不存在。")))
                .then();
    }

    public Mono<AiTeam> removeMember(String teamName, String userId) {
        return getTeam(teamName).flatMap(team -> {
            var spec = team.getSpec();
            if (spec == null || spec.getMembers() == null) {
                return Mono.just(team);
            }
            spec.getMembers().stream()
                    .filter(member -> userId.equals(member.getUserId()))
                    .forEach(member -> member.setStatus(AiTeam.MemberStatus.REMOVED));
            return client.update(team);
        });
    }
}
