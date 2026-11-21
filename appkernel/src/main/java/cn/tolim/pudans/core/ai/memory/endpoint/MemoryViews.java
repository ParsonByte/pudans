package cn.tolim.pudans.core.ai.memory.endpoint;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.MemoryRecall;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MemoryViews {

    private static final String CODE_FORBIDDEN = "ai-memory-forbidden";

    private static final String CODE_NOT_FOUND = "ai-memory-not-found";

    private final AiSettings aiSettings;

    private final Teams teams;

    private final MemoryRecall memoryRecall;

    private final AiMemoryL1Repository l1Repository;

    private final AiMemoryL2Repository l2Repository;

    private final AiMemoryL3Repository l3Repository;

    public Mono<List<L1MemoryRecord>> list(
            String userId,
            String team,
            String agent,
            MemoryType type,
            long beforeId,
            int limit) {
        return scope(userId, team, agent).flatMap(scope -> l1Repository
                .listByUser(scope.team(), scope.agent(), userId, type, beforeId, limit)
                .collectList());
    }

    public Mono<List<MemoryInjection.Ref>> search(
            String userId, String team, String agent, String query, int limit) {
        return scope(userId, team, agent).flatMap(scope -> memoryRecall
                .build(
                        new MemoryContext(scope.team(), userId, scope.agent(), "user-search", null),
                        query,
                        limit,
                        0)
                .map(injection -> injection.refs().stream().limit(limit).toList()));
    }

    public Flux<L2SceneBlock> scenes(String userId, String team, String agent) {
        return scope(userId, team, agent)
                .flatMapMany(scope -> l2Repository.findByScope(scope.team(), scope.agent()));
    }

    public Mono<L3Persona> profile(String userId, String team, String agent) {
        return scope(userId, team, agent)
                .flatMap(scope -> l3Repository.findByScope(scope.team(), scope.agent()));
    }

    public Mono<L1MemoryRecord> detail(String userId, String name, String team, String agent) {
        return scope(userId, team, agent).flatMap(scope -> {
            var id = id(name);
            return l1Repository
                    .findByIds(List.of(id))
                    .filter(record -> userId.equals(record.context().userId())
                            && scope.team().equals(record.context().teamId())
                            && scope.agent().equals(record.context().agentId()))
                    .next()
                    .switchIfEmpty(Mono.error(new ApiException(
                            HttpStatus.NOT_FOUND, CODE_NOT_FOUND, "记忆不存在。")));
        });
    }

    private Mono<Scope> scope(String userId, String team, String agent) {
        return aiSettings.spec().flatMap(setting -> {
            var defaults = setting.getDefaults() == null ? new AiSetting.Defaults() : setting.getDefaults();
            var agentName = StringUtils.hasText(agent)
                    ? agent
                    : StringUtils.hasText(defaults.getAgent()) ? defaults.getAgent() : Teams.DEFAULT_AGENT;
            return teams.requireAgent(agentName, userId).flatMap(found -> {
                var teamName = StringUtils.hasText(team) ? team : found.getSpec().getTeam();
                if (!teamName.equals(found.getSpec().getTeam())) {
                    return Mono.error(new ApiException(
                            HttpStatus.FORBIDDEN, CODE_FORBIDDEN, "无权访问该团队的记忆。"));
                }
                return Mono.just(new Scope(teamName, found.getMetadata().getName()));
            });
        });
    }

    private static long id(String name) {
        var value = name == null ? "" : name;
        var separator = value.indexOf(':');
        if (separator >= 0) {
            value = value.substring(separator + 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_NOT_FOUND, "记忆标识格式不正确。");
        }
    }

    private record Scope(String team, String agent) {}
}
